package com.group4.case1;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.interceptor.auth.BasicAuthProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SpeditionController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final String CAMUNDA_BASE_URL = getEnvOrDefault(
            "CAMUNDA_ENGINE_REST_URL",
            "http://group4:group4@192.168.111.3:8080/engine-rest"
    );
    private static final String CAMUNDA_USERNAME = getEnvOrDefault("CAMUNDA_USERNAME", "group4");
    private static final String CAMUNDA_PASSWORD = getEnvOrDefault("CAMUNDA_PASSWORD", "PLVbIZynDiaW6K");
    private static final String SPEDITION_BASE_URL = getEnvOrDefault(
            "SPEDITION_BASE_URL",
            "http://192.168.111.5:8080/v1"
    );
    private static final String SPEDITION_REQUEST_PATH = "/consignment/request";
    private static final String CAMUNDA_TOPIC = "group4_rest";

    public static void main(String[] args) {
        printInfo("Starting logistics external task worker (topic=" + CAMUNDA_TOPIC
                + ", camundaUrl=" + sanitizeUrlForLog(stripUserInfoFromUrl(CAMUNDA_BASE_URL))
                + ", speditionUrl=" + sanitizeUrlForLog(SPEDITION_BASE_URL) + ")");

        String camundaBaseUrlWithoutCredentials = stripUserInfoFromUrl(CAMUNDA_BASE_URL);

        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl(camundaBaseUrlWithoutCredentials)
                .addInterceptor(new BasicAuthProvider(CAMUNDA_USERNAME, CAMUNDA_PASSWORD))
                .asyncResponseTimeout(10000)
                .build();

        printInfo("Camunda BasicAuth configured (username=" + maskValueForLog(CAMUNDA_USERNAME) + ")");

        client.subscribe(CAMUNDA_TOPIC)
                .lockDuration(30_000)
                .handler((externalTask, externalTaskService) -> {
                    printInfo("Received external task (taskId=" + externalTask.getId()
                            + ", topic=" + CAMUNDA_TOPIC
                            + ", retries=" + externalTask.getRetries() + ")");
                    try {
                        String orderId = readRequiredString(externalTask.getVariable("order_nr"), "order_nr", 128);
                        int weight = readRequiredWeight(externalTask.getVariable("weight"));
                        String address = readRequiredString(externalTask.getVariable("delivery_address"), "delivery_address", 512);
                        String phone = readRequiredString(externalTask.getVariable("phone"), "phone", 64);
                        printInfo("Validated Camunda input (taskId=" + externalTask.getId()
                                + ", orderNr=" + maskValueForLog(orderId)
                                + ", weightKg=" + weight
                                + ", addressLength=" + safeLength(address)
                                + ", phone=" + maskPhoneForLog(phone) + ")");

                        // Nur die vier fachlich geforderten Felder werden an den Spediteur gesendet.
                        NewConsignmentRequest payload = new NewConsignmentRequest();
                        payload.destination = address;
                        payload.customerReference = orderId;
                        payload.recepientPhone = phone;
                        payload.weight = weight;
                        printInfo("Sending consignment request to logistics provider (taskId="
                                + externalTask.getId()
                                + ", endpoint=" + sanitizeUrlForLog(SPEDITION_BASE_URL + SPEDITION_REQUEST_PATH)
                                + ", payloadFields=4)");

                        HttpResponse<String> response = sendConsignmentRequest(payload);
                        int statusCode = response.statusCode();
                        printInfo("Received logistics API response (taskId=" + externalTask.getId()
                                + ", statusCode=" + statusCode
                                + ", bodyLength=" + safeLength(response.body()) + ")");

                        if (statusCode == 200 || statusCode == 202) {
                            ConsignmentResponse consignment = parseConsignmentResponse(response.body());
                            printInfo("Logistics order created successfully (taskId=" + externalTask.getId()
                                    + ", consignmentId=" + maskValueForLog(consignment.orderId)
                                    + ", pickupDate=" + nullToDash(consignment.pickupdate)
                                    + ", deliveryDate=" + nullToDash(consignment.deliverydate) + ")");
                            externalTaskService.complete(
                                    externalTask,
                                    buildSuccessVariables(statusCode, consignment, response.body())
                            );
                            printInfo("Completed external task successfully (taskId=" + externalTask.getId() + ")");
                            return;
                        }

                        if (statusCode == 405 || statusCode == 501) {
                            printWarn("Logistics provider returned business error (taskId=" + externalTask.getId()
                                    + ", statusCode=" + statusCode
                                    + ", message=" + mapBusinessError(statusCode) + ")");
                            externalTaskService.complete(
                                    externalTask,
                                    buildBusinessErrorVariables(statusCode, response.body())
                            );
                            printInfo("Completed external task with business error variables (taskId=" + externalTask.getId() + ")");
                            return;
                        }

                        String responseBody = truncateForLog(response.body());
                        printWarn("Unexpected logistics API response status=" + statusCode + " body=" + responseBody);
                        externalTaskService.handleFailure(
                                externalTask,
                                "Unexpected logistics API response: HTTP " + statusCode,
                                responseBody,
                                getNextRetries(externalTask.getRetries()),
                                60_000L
                        );
                    } catch (IllegalArgumentException e) {
                        printWarn("Invalid external task input for taskId=" + externalTask.getId() + ": " + e.getMessage());
                        externalTaskService.complete(externalTask, buildInputErrorVariables());
                        printInfo("Completed external task with input-error variables (taskId=" + externalTask.getId() + ")");
                    } catch (IOException | InterruptedException e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        printError("Logistics API call failed for taskId=" + externalTask.getId() + ": " + e.getMessage());
                        externalTaskService.handleFailure(
                                externalTask,
                                "Logistics API call failed",
                                e.getClass().getSimpleName() + ": " + e.getMessage(),
                                getNextRetries(externalTask.getRetries()),
                                60_000L
                        );
                    } catch (Exception e) {
                        printError("Unexpected error while processing logistics task " + externalTask.getId()
                                + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        e.printStackTrace(System.err);
                        externalTaskService.handleFailure(
                                externalTask,
                                "Unexpected logistics worker error",
                                e.getClass().getSimpleName() + ": " + e.getMessage(),
                                getNextRetries(externalTask.getRetries()),
                                60_000L
                        );
                    }
                })
                .open();

        printInfo("External task subscription opened (topic=" + CAMUNDA_TOPIC + ", lockDurationMs=30000)");
    }

    private static HttpResponse<String> sendConsignmentRequest(NewConsignmentRequest payload)
            throws IOException, InterruptedException {
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

    private static URI buildSpeditionUri() {
        String base = SPEDITION_BASE_URL.endsWith("/")
                ? SPEDITION_BASE_URL.substring(0, SPEDITION_BASE_URL.length() - 1)
                : SPEDITION_BASE_URL;
        try {
            return new URI(base + SPEDITION_REQUEST_PATH);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid SPEDITION_BASE_URL configuration", e);
        }
    }

    private static ConsignmentResponse parseConsignmentResponse(String responseBody) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(responseBody, ConsignmentResponse.class);
    }

    private static Map<String, Object> buildSuccessVariables(int statusCode, ConsignmentResponse consignment, String responseBody) {
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

    private static Map<String, Object> buildBusinessErrorVariables(int statusCode, String responseBody) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("spedition_request_success", false);
        variables.put("spedition_http_code", statusCode);
        variables.put("spedition_error_message", mapBusinessError(statusCode));
        variables.put("spedition_response_body", sanitizeBodyForVariable(responseBody));
        return variables;
    }

    private static Map<String, Object> buildInputErrorVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("spedition_request_success", false);
        variables.put("spedition_http_code", 405);
        variables.put("spedition_error_message", "Ungueltige Eingabedaten fuer Speditionsauftrag");
        variables.put("spedition_response_body", "");
        return variables;
    }

    private static String readRequiredString(Object rawValue, String variableName, int maxLength) {
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

    private static int readRequiredWeight(Object rawValue) {
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

    private static int getNextRetries(Integer currentRetries) {
        int retries = currentRetries == null ? 3 : currentRetries;
        return Math.max(retries - 1, 0);
    }

    private static String truncateForLog(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private static String sanitizeBodyForVariable(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return sanitized.length() > 2000 ? sanitized.substring(0, 2000) : sanitized;
    }

    private static String mapBusinessError(int statusCode) {
        return switch (statusCode) {
            case 405 -> "Spediteur meldet ungueltige Eingabedaten";
            case 501 -> "Spediteur kann Auftrag nicht automatisch verarbeiten (z.B. Gewicht zu hoch)";
            default -> "Unbekannter Spediteur-Fehler";
        };
    }

    private static String coalesce(String first, String second) {
        return first != null ? first : second;
    }

    private static void printInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    private static void printWarn(String message) {
        System.out.println("[WARN] " + message);
    }

    private static void printError(String message) {
        System.err.println("[ERROR] " + message);
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String maskValueForLog(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 6) {
            return "***";
        }
        return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 3);
    }

    private static String maskPhoneForLog(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String digitsOnly = value.replaceAll("\\D", "");
        if (digitsOnly.length() < 4) {
            return "***";
        }
        return "***" + digitsOnly.substring(digitsOnly.length() - 4);
    }

    private static String sanitizeUrlForLog(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "-";
        }
        try {
            URI uri = new URI(rawUrl);
            StringBuilder sb = new StringBuilder();
            if (uri.getScheme() != null) {
                sb.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                sb.append(uri.getHost());
            }
            if (uri.getPort() != -1) {
                sb.append(':').append(uri.getPort());
            }
            if (uri.getPath() != null) {
                sb.append(uri.getPath());
            }
            return sb.toString();
        } catch (URISyntaxException e) {
            return "<invalid-url>";
        }
    }

    private static String stripUserInfoFromUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }
        try {
            URI uri = new URI(rawUrl);
            URI cleaned = new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
            return cleaned.toString();
        } catch (URISyntaxException e) {
            return rawUrl;
        }
    }

    private static String getEnvOrDefault(String envName, String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
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
