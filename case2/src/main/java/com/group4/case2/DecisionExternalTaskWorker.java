package com.group4.case2;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.ExternalTaskClientBuilder;
import org.camunda.bpm.client.interceptor.auth.BasicAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DecisionExternalTaskWorker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DecisionExternalTaskWorker.class);

    private final ShippingDecisionService shippingDecisionService;

    @Value("${case2.camunda.engine-rest-url}")
    private String camundaBaseUrl;

    @Value("${case2.camunda.username}")
    private String camundaUsername;

    @Value("${case2.camunda.password}")
    private String camundaPassword;

    @Value("${case2.camunda.topic}")
    private String camundaTopic;

    public DecisionExternalTaskWorker(ShippingDecisionService shippingDecisionService) {
        this.shippingDecisionService = shippingDecisionService;
    }

    @Override
    public void run(String... args) {
        log.info(
                "Starting Camunda external task worker: engineRestUrl={}, topic={}, username={}",
                camundaBaseUrl,
                camundaTopic,
                camundaUsername.isBlank() ? "<none>" : camundaUsername
        );

        ExternalTaskClientBuilder builder = ExternalTaskClient.create()
                .baseUrl(camundaBaseUrl)
                .asyncResponseTimeout(10000);

        if (!camundaUsername.isBlank()) {
            builder.addInterceptor(new BasicAuthProvider(camundaUsername, camundaPassword));
        }

        ExternalTaskClient client = builder.build();

        client.subscribe(camundaTopic)
                .lockDuration(30_000)
                .handler((externalTask, externalTaskService) -> {
                    bindTaskMdc(externalTask.getId(), externalTask.getProcessInstanceId(), externalTask.getExecutionId());
                    try {
                        log.info(
                                "External task received: topic={}, activityId={}, businessKey={}, retries={}",
                                externalTask.getTopicName(),
                                externalTask.getActivityId(),
                                externalTask.getBusinessKey(),
                                externalTask.getRetries()
                        );

                        DecisionInput input = new DecisionInput(
                                readRequiredString(externalTask.getVariable("order_nr"), "order_nr", 128),
                                readRequiredString(externalTask.getVariable("client_nr"), "client_nr", 128),
                                normalizeCountry(readRequiredString(externalTask.getVariable("delivery_country"), "delivery_country", 2)),
                                readRequiredString(externalTask.getVariable("delivery_address"), "delivery_address", 512),
                                readRequiredWeight(externalTask.getVariable("weight")),
                                readRequiredString(externalTask.getVariable("phone"), "phone", 64),
                                readRequiredString(externalTask.getVariable("mail"), "mail", 256)
                        );

                        log.info(
                                "Decision input validated: orderNr={}, clientNr={}, country={}, weightKg={}, mail={}, phone={}",
                                input.orderNr(),
                                input.clientNr(),
                                input.deliveryCountry(),
                                input.weightKg(),
                                maskMail(input.mail()),
                                maskPhone(input.phone())
                        );

                        DecisionResult result = shippingDecisionService.decide(input);

                        log.info(
                                "Decision produced: decisionId={}, outcome={}, autoContinue={}, channel={}, ruleId={}, ruleVersion={}",
                                result.getDecisionId(),
                                result.getOutcome(),
                                result.isAutoContinue(),
                                result.getRecommendedChannel(),
                                result.getRuleId(),
                                result.getRuleVersion()
                        );

                        externalTaskService.complete(externalTask, result.toProcessVariables());
                        log.info("External task completed successfully with decisionId={}", result.getDecisionId());
                    } catch (IllegalArgumentException e) {
                        DecisionResult fallbackResult = buildManualFallbackResult(
                                "INPUT_VALIDATION_ERROR",
                                "Ungueltige oder unvollstaendige Entscheidungsdaten: " + e.getMessage()
                        );
                        externalTaskService.complete(externalTask, fallbackResult.toProcessVariables());
                        log.warn(
                                "Input validation failed. Manual-review fallback completed: decisionId={}, reason={}",
                                fallbackResult.getDecisionId(),
                                fallbackResult.getReason()
                        );
                    } catch (Exception e) {
                        int nextRetries = getNextRetries(externalTask.getRetries());
                        log.error(
                                "Decision worker failed: nextRetries={}, error={}",
                                nextRetries,
                                e.getMessage(),
                                e
                        );
                        externalTaskService.handleFailure(
                                externalTask,
                                "Decision worker error",
                                e.getClass().getSimpleName() + ": " + e.getMessage(),
                                nextRetries,
                                60_000L
                        );
                    } finally {
                        MDC.clear();
                    }
                })
                .open();

        log.info("Camunda external task subscription opened for topic={}", camundaTopic);
    }

    private DecisionResult buildManualFallbackResult(String ruleId, String reason) {
        DecisionResult result = new DecisionResult();
        result.setRuleId(ruleId);
        result.setReason(reason);
        result.setRecommendedChannel(ShippingChannel.MANUAL_REVIEW);
        result.setOutcome(DecisionOutcome.MANUAL_REVIEW_REQUIRED);
        result.setAutoContinue(false);
        return result;
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

    private String maskMail(String mail) {
        int atIndex = mail.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return mail.charAt(0) + "***" + mail.substring(atIndex);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 2) {
            return "**";
        }
        return "*".repeat(phone.length() - 2) + phone.substring(phone.length() - 2);
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

        if (rawValue instanceof Number number) {
            int value = number.intValue();
            if (value <= 0) {
                throw new IllegalArgumentException("Weight must be > 0");
            }
            return value;
        }

        try {
            int value = Integer.parseInt(String.valueOf(rawValue).trim());
            if (value <= 0) {
                throw new IllegalArgumentException("Weight must be > 0");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid weight value");
        }
    }

    private String normalizeCountry(String country) {
        return country.trim().toUpperCase();
    }

    private int getNextRetries(Integer currentRetries) {
        int retries = currentRetries == null ? 3 : currentRetries;
        return Math.max(retries - 1, 0);
    }
}
