package com.group4.case2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ShippingDecisionService {

    private static final Logger log = LoggerFactory.getLogger(ShippingDecisionService.class);

    private final DroolsRuleEvaluator droolsRuleEvaluator;
    private final DecisionLogRepository decisionLogRepository;

    public ShippingDecisionService(
            DroolsRuleEvaluator droolsRuleEvaluator,
            DecisionLogRepository decisionLogRepository
    ) {
        this.droolsRuleEvaluator = droolsRuleEvaluator;
        this.decisionLogRepository = decisionLogRepository;
    }

    public DecisionResult decide(DecisionInput input) {
        log.info(
                "Evaluating shipping decision: orderNr={}, clientNr={}, country={}, weightKg={}",
                input.orderNr(),
                input.clientNr(),
                input.deliveryCountry(),
                input.weightKg()
        );

        DecisionResult result = droolsRuleEvaluator.evaluate(input);

        log.info(
                "Decision evaluation finished: decisionId={}, outcome={}, autoContinue={}, channel={}, ruleId={}",
                result.getDecisionId(),
                result.getOutcome(),
                result.isAutoContinue(),
                result.getRecommendedChannel(),
                result.getRuleId()
        );

        decisionLogRepository.save(input, result);
        log.info("Decision persisted to database: decisionId={}, orderNr={}", result.getDecisionId(), input.orderNr());
        return result;
    }
}
