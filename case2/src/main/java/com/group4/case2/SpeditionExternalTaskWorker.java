package com.group4.case2;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.ExternalTaskClientBuilder;
import org.camunda.bpm.client.interceptor.auth.BasicAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class SpeditionExternalTaskWorker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SpeditionExternalTaskWorker.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Value("${case2.camunda.engine-rest-url}")
    private String camundaBaseUrl;

    @Value("${case2.camunda.username}")
    private String camundaUsername;

    @Value("${case2.camunda.password}")
    private String camundaPassword;

    @Value("${case2.camunda.shipping-topic}")
    private String shippingTopic;

    @Value("${case2.spedition.base-url}")
    private String speditionBaseUrl;

    @Value("${case2.spedition.request-path}")
    private String speditionRequestPath;

    @Override
    public void run(String... args) {
        log.info(
                "Starting shipping external task worker: engineRestUrl={}, topic={}, speditionBaseUrl={}, requestPath={}",
                camundaBaseUrl,
                shippingTopic,
                speditionBaseUrl,
                speditionRequestPath
        );

        ExternalTaskClientBuilder builder = ExternalTaskClient.create()
                .baseUrl(camundaBaseUrl)
                .asyncResponseTimeout(10000);

        if (!camundaUsername.isBlank()) {
            builder.addInterceptor(new BasicAuthProvider(camundaUsername, camundaPassword));
        }

        ExternalTaskClient client = builder.build();

        client.subscribe(shippingTopic)
                .lockDuration(30_000)
                .handler((externalTask, externalTaskService) -> {
                    bindTaskMdc(externalTask.getId(), externalTask.getProcessInstanceId(), externalTask.getExecutionId());
                    try {
                        log.info(
                                "Shipping external task received: topic={}, activityId={}, businessKey={}, retries={}",
                                externalTask.getTopicName(),
                                externalTask.getActivityId(),
                                externalTask.getBusinessKey(),
                                externalTask.getRetries()
                        );

                        String orderId = readRequiredString(externalTask.getVariable("order_nr"), "order_nr", 128);
                        int weight = readRequiredWeight(externalTask.getVariable("weight"));
                        String address = readRequiredString(externalTask.getVariable("delivery_address"), "delivery_address", 512);
                        String phone = readRequiredString(externalTask.getVariable("phone"), "phone", 64);

                        log.info(
                                "Shipping input validated: orderNr={}, weightKg={}, phone={}, addressLength={}",
                                orderId,
                                weight,
                                maskPhone(phone),
                                address.length()
                        );

                        NewConsignmentRequest payload = new NewConsignmentRequest();
                        payload.destination = address;
                        payload.customerReference = orderId;
                        payload.recepientPhone = phone;
                        payload.weight = weight;

                        HttpResponse<String> response = sendConsignmentRequest(payload);
                        int statusCode = response.statusCode();
                        String responseBody = response.body();
                        log.info(
                                "Shipping API responded: statusCode={}, bodyLength={}",
                                statusCode,
                                responseBody == null ? 0 : responseBody.length()
                        );

                        if (statusCode == 200 || statusCode == 202) {
                            ConsignmentResponse consignment = parseConsignmentResponse(responseBody);
                            externalTaskService.complete(externalTask, buildSuccessVariables(statusCode, consignment, responseBody));
                            log.info(
                                    "Shipping task completed successfully: statusCode={}, speditionOrderId={}",
                                    statusCode,
                                    consignment.orderId
                            );
                            return;
                        }

                        if (statusCode == 405 || statusCode == 501) {
                            externalTaskService.complete(externalTask, buildBusinessErrorVariables(statusCode, responseBody));
                            log.warn(
                                    "Shipping task completed with business error variables: statusCode={}, message={}",
                                    statusCode,
                                    mapBusinessError(statusCode)
                            );
                            return;
                        }

                        int nextRetries = getNextRetries(externalTask.getRetries());
                        log.error(
                                "Unexpected shipping API status: statusCode={}, nextRetries={}, body={}",
                                statusCode,
                                nextRetries,
                                truncateForLog(responseBody)
                        );
                        externalTaskService.handleFailure(
                                externalTask,
                                "Unexpected logistics API response: HTTP " + statusCode,
                                truncateForLog(responseBody),
                                nextRetries,
                                60_000L
                        );
                    } catch (IllegalArgumentException e) {
                        externalTaskService.complete(externalTask, buildInputErrorVariables());
                        log.warn("Shipping input validation failed. Completed with input-error variables: {}", e.getMessage());
                    } catch (IOException | InterruptedException e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        int nextRetries = getNextRetries(externalTask.getRetries());
                        log.error(
                                "Shipping API call failed: nextRetries={}, error={}",
                                nextRetries,
                                e.getMessage(),
                                e
                        );
                        externalTaskService.handleFailure(
                                externalTask,
                                "Logistics API call failed",
                                e.getClass().getSimpleName() + ": " + e.getMessage(),
                                nextRetries,
                                60_000L
                        );
                    } catch (Exception e) {
                        int nextRetries = getNextRetries(externalTask.getRetries());
                        log.error(
                                "Unexpected shipping worker error: nextRetries={}, error={}",
                                nextRetries,
                                e.getMessage(),
                                e
                        );
                        externalTaskService.handleFailure(
                                externalTask,
                                "Unexpected logistics worker error",
                                e.getClass().getSimpleName() + ": " + e.getMessage(),
                                nextRetries,
                                60_000L
                        );
                    } finally {
                        MDC.clear();
                    }
                })
                .open();

        log.info("Shipping external task subscription opened for topic={}", shippingTopic);
    }

    private HttpResponse<String> sendConsignmentRequest(NewConsignmentRequest payload) throws IOException, InterruptedException {
        String body = OBJECT_MAPPER.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildSpeditionUri())
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI buildSpeditionUri() {
        String base = speditionBaseUrl.endsWith("/")
                ? speditionBaseUrl.substring(0, speditionBaseUrl.length() - 1)
                : speditionBaseUrl;

        String path = speditionRequestPath.startsWith("/") ? speditionRequestPath : "/" + speditionRequestPath;

        try {
            return new URI(base + path);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid spedition URL configuration", e);
        }
    }

    private ConsignmentResponse parseConsignmentResponse(String responseBody) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(responseBody, ConsignmentResponse.class);
    }

    private Map<String, Object> buildSuccessVariables(int statusCode, ConsignmentResponse consignment, String responseBody) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("spedition_request_success", true);
        variables.put("spedition_http_code", statusCode);
        variables.put("spedition_order_id", consignment.orderId);
        variables.put("spedition_pickupdate", consignment.pickupdate);
        variables.put("spedition_deliverydate", consignment.deliverydate);
        variables.put("spedition_customer_reference", coalesce(consignment.customerReference, consignment.customerRefernce));
        variables.put("spedition_recepient_phone", consignment.recepientPhone);
        variables.put("spedition_destination", consignment.destination);
        variables.put("spedition_weight", consignment.weight);
        variables.put("spedition_response_body", sanitizeBodyForVariable(responseBody));
        return variables;
    }

    private Map<String, Object> buildBusinessErrorVariables(int statusCode, String responseBody) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("spedition_request_success", false);
        variables.put("spedition_http_code", statusCode);
        variables.put("spedition_error_message", mapBusinessError(statusCode));
        variables.put("spedition_response_body", sanitizeBodyForVariable(responseBody));
        return variables;
    }

    private Map<String, Object> buildInputErrorVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("spedition_request_success", false);
        variables.put("spedition_http_code", 405);
        variables.put("spedition_error_message", "Ungueltige Eingabedaten fuer Speditionsauftrag");
        variables.put("spedition_response_body", "");
        return variables;
    }

    private String readRequiredString(Object rawValue, String variableName, int maxLength) {
        if (rawValue == null) {
            throw new IllegalArgumentException("Missing process variable: " + variableName);
        }

        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Empty process variable: " + variableName);
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Process variable too long: " + variableName);
        }
        return value;
    }

    private int readRequiredWeight(Object rawValue) {
        if (rawValue == null) {
            throw new IllegalArgumentException("Missing process variable: weight");
        }

        int parsed;
        if (rawValue instanceof Number number) {
            parsed = number.intValue();
        } else {
            String text = String.valueOf(rawValue).trim();
            try {
                parsed = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid weight value");
            }
        }

        if (parsed <= 0) {
            throw new IllegalArgumentException("Weight must be > 0");
        }
        return parsed;
    }

    private int getNextRetries(Integer currentRetries) {
        int retries = currentRetries == null ? 3 : currentRetries;
        return Math.max(retries - 1, 0);
    }

    private String truncateForLog(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private String sanitizeBodyForVariable(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return sanitized.length() > 2000 ? sanitized.substring(0, 2000) : sanitized;
    }

    private String mapBusinessError(int statusCode) {
        return switch (statusCode) {
            case 405 -> "Spediteur meldet ungueltige Eingabedaten";
            case 501 -> "Spediteur kann Auftrag nicht automatisch verarbeiten (z.B. Gewicht zu hoch)";
            default -> "Unbekannter Spediteur-Fehler";
        };
    }

    private String coalesce(String first, String second) {
        return first != null ? first : second;
    }

    private void bindTaskMdc(String taskId, String processInstanceId, String executionId) {
        if (taskId != null) {
            MDC.put("taskId", taskId);
        }
        if (processInstanceId != null) {
            MDC.put("procInstId", processInstanceId);
        }
        if (executionId != null) {
            MDC.put("executionId", executionId);
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 2) {
            return "**";
        }
        return "*".repeat(phone.length() - 2) + phone.substring(phone.length() - 2);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConsignmentResponse {
        public String orderId;
        public Integer weight;
        public String pickupdate;
        public String deliverydate;
        public String customerReference;
        @JsonAlias("customerRefernce")
        public String customerRefernce;
        public String recepientPhone;
        public String destination;
    }

    public static class NewConsignmentRequest {
        public String destination;
        public String customerReference;
        public String recepientPhone;
        public Integer weight;
    }
}
