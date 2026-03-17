package com.group4.case2;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

@Component
public class DroolsRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DroolsRuleEvaluator.class);

    private final KieContainer kieContainer;

    public DroolsRuleEvaluator() {
        this.kieContainer = KieServices.Factory.get().getKieClasspathContainer();
    }

    public DecisionResult evaluate(DecisionInput input) {
        log.debug(
                "Starting Drools evaluation: orderNr={}, country={}, weightKg={}",
                input.orderNr(),
                input.deliveryCountry(),
                input.weightKg()
        );

        KieSession kieSession = kieContainer.newKieSession("shippingRulesSession");
        try {
            DecisionContext context = new DecisionContext(input);
            kieSession.insert(context);
            int firedRules = kieSession.fireAllRules();
            DecisionResult result = context.getResult();

            log.info(
                    "Drools evaluation complete: decisionId={}, firedRules={}, outcome={}, channel={}, ruleId={}, ruleVersion={}",
                    result.getDecisionId(),
                    firedRules,
                    result.getOutcome(),
                    result.getRecommendedChannel(),
                    result.getRuleId(),
                    result.getRuleVersion()
            );

            return result;
        } finally {
            kieSession.dispose();
        }
    }

    @PreDestroy
    public void shutdown() {
        kieContainer.dispose();
    }
}
