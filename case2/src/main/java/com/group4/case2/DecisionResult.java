package com.group4.case2;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class DecisionResult {

    private final String decisionId = UUID.randomUUID().toString();
    private DecisionOutcome outcome = DecisionOutcome.MANUAL_REVIEW_REQUIRED;
    private boolean autoContinue = false;
    private ShippingChannel recommendedChannel = ShippingChannel.MANUAL_REVIEW;
    private String ruleId = "DEFAULT_MANUAL_REVIEW";
    private String ruleVersion = "v1";
    private String reason = "Keine freigegebene automatische Versandregel verfuegbar.";

    public String getDecisionId() {
        return decisionId;
    }

    public DecisionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(DecisionOutcome outcome) {
        this.outcome = outcome;
    }

    public boolean isAutoContinue() {
        return autoContinue;
    }

    public void setAutoContinue(boolean autoContinue) {
        this.autoContinue = autoContinue;
    }

    public ShippingChannel getRecommendedChannel() {
        return recommendedChannel;
    }

    public void setRecommendedChannel(ShippingChannel recommendedChannel) {
        this.recommendedChannel = recommendedChannel;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> toProcessVariables() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("decision_id", decisionId);
        variables.put("decision_outcome", outcome.name());
        variables.put("decision_auto_continue", autoContinue);
        variables.put("decision_recommended_channel", recommendedChannel.name());
        variables.put("decision_rule_id", ruleId);
        variables.put("decision_rule_version", ruleVersion);
        variables.put("decision_reason", reason);
        return variables;
    }
}
