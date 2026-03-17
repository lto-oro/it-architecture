package com.group4.case2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroolsRuleEvaluatorTests {

    private final DroolsRuleEvaluator evaluator = new DroolsRuleEvaluator();

    @Test
    void routesSwitzerlandToAutomaticStandardShipping() {
        DecisionResult result = evaluator.evaluate(input("CH", 120));

        assertEquals(DecisionOutcome.STANDARD_SPEDITION_AUTOMATED, result.getOutcome());
        assertTrue(result.isAutoContinue());
        assertEquals(ShippingChannel.STANDARD_SPEDITION, result.getRecommendedChannel());
        assertEquals("CH_DE_STANDARD_SPEDITION", result.getRuleId());
    }

    @Test
    void routesGermanyToAutomaticStandardShipping() {
        DecisionResult result = evaluator.evaluate(input("DE", 950));

        assertEquals(DecisionOutcome.STANDARD_SPEDITION_AUTOMATED, result.getOutcome());
        assertTrue(result.isAutoContinue());
        assertEquals(ShippingChannel.STANDARD_SPEDITION, result.getRecommendedChannel());
        assertEquals("CH_DE_STANDARD_SPEDITION", result.getRuleId());
    }

    @Test
    void routesRussiaToManualSanctionsReview() {
        DecisionResult result = evaluator.evaluate(input("RU", 10));

        assertEquals(DecisionOutcome.MANUAL_REVIEW_REQUIRED, result.getOutcome());
        assertFalse(result.isAutoContinue());
        assertEquals(ShippingChannel.SANCTIONS_REVIEW, result.getRecommendedChannel());
        assertEquals("RU_MANUAL_SANCTIONS_REVIEW", result.getRuleId());
    }

    @Test
    void routesArgentinaBelow60ToPostManualPath() {
        DecisionResult result = evaluator.evaluate(input("AR", 59));

        assertEquals(DecisionOutcome.MANUAL_REVIEW_REQUIRED, result.getOutcome());
        assertFalse(result.isAutoContinue());
        assertEquals(ShippingChannel.POST, result.getRecommendedChannel());
        assertEquals("AR_POST_UNAVAILABLE", result.getRuleId());
    }

    @Test
    void routesArgentinaAt60ToSpecialCarrierManualPath() {
        DecisionResult result = evaluator.evaluate(input("AR", 60));

        assertEquals(DecisionOutcome.MANUAL_REVIEW_REQUIRED, result.getOutcome());
        assertFalse(result.isAutoContinue());
        assertEquals(ShippingChannel.SPECIAL_SPEDITION_ARGENTINA, result.getRecommendedChannel());
        assertEquals("AR_SPECIAL_SPEDITION_UNAVAILABLE", result.getRuleId());
    }

    @Test
    void routesArgentinaAt500ToSpecialCarrierManualPath() {
        DecisionResult result = evaluator.evaluate(input("AR", 500));

        assertEquals(DecisionOutcome.MANUAL_REVIEW_REQUIRED, result.getOutcome());
        assertFalse(result.isAutoContinue());
        assertEquals(ShippingChannel.SPECIAL_SPEDITION_ARGENTINA, result.getRecommendedChannel());
        assertEquals("AR_SPECIAL_SPEDITION_UNAVAILABLE", result.getRuleId());
    }

    @Test
    void routesArgentinaAbove500ToManualPath() {
        DecisionResult result = evaluator.evaluate(input("AR", 501));

        assertEquals(DecisionOutcome.MANUAL_REVIEW_REQUIRED, result.getOutcome());
        assertFalse(result.isAutoContinue());
        assertEquals(ShippingChannel.MANUAL_REVIEW, result.getRecommendedChannel());
        assertEquals("AR_OVER_500_MANUAL", result.getRuleId());
    }

    @Test
    void routesJapanAt200ToAirFreightManualPath() {
        DecisionResult result = evaluator.evaluate(input("JP", 200));

        assertEquals(DecisionOutcome.MANUAL_REVIEW_REQUIRED, result.getOutcome());
        assertFalse(result.isAutoContinue());
        assertEquals(ShippingChannel.AIR_FREIGHT, result.getRecommendedChannel());
        assertEquals("JP_AIR_FREIGHT_UNAVAILABLE", result.getRuleId());
    }

    @Test
    void routesJapanAbove200ToDefaultManualRule() {
        DecisionResult result = evaluator.evaluate(input("JP", 201));

        assertEquals(DecisionOutcome.MANUAL_REVIEW_REQUIRED, result.getOutcome());
        assertFalse(result.isAutoContinue());
        assertEquals(ShippingChannel.MANUAL_REVIEW, result.getRecommendedChannel());
        assertEquals("DEFAULT_MANUAL_REVIEW", result.getRuleId());
    }

    @Test
    void routesUnknownCountryToDefaultManualRule() {
        DecisionResult result = evaluator.evaluate(input("US", 20));

        assertEquals(DecisionOutcome.MANUAL_REVIEW_REQUIRED, result.getOutcome());
        assertFalse(result.isAutoContinue());
        assertEquals(ShippingChannel.MANUAL_REVIEW, result.getRecommendedChannel());
        assertEquals("DEFAULT_MANUAL_REVIEW", result.getRuleId());
    }

    private DecisionInput input(String country, int weightKg) {
        return new DecisionInput(
                "4711",
                "100",
                country,
                "Musterstrasse 1, Basel",
                weightKg,
                "+41611234567",
                "kunde@example.org"
        );
    }
}
