package com.group4.case2;

public class DecisionContext {

    private final DecisionInput input;
    private final DecisionResult result;

    public DecisionContext(DecisionInput input) {
        this.input = input;
        this.result = new DecisionResult();
    }

    public DecisionInput getInput() {
        return input;
    }

    public DecisionResult getResult() {
        return result;
    }

    public void setDecision(
            DecisionOutcome outcome,
            boolean autoContinue,
            ShippingChannel channel,
            String ruleId,
            String reason
    ) {
        result.setOutcome(outcome);
        result.setAutoContinue(autoContinue);
        result.setRecommendedChannel(channel);
        result.setRuleId(ruleId);
        result.setReason(reason);
    }
}
