package com.group4.case2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class DecisionLogRepository {

    private static final Logger log = LoggerFactory.getLogger(DecisionLogRepository.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INSERT_SQL = """
            INSERT INTO shipping_decision_log (
                id,
                order_nr,
                client_nr,
                delivery_country,
                delivery_address,
                weight_kg,
                phone,
                mail,
                outcome,
                auto_continue,
                recommended_channel,
                rule_id,
                rule_version,
                reason,
                payload_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public DecisionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(DecisionInput input, DecisionResult result) {
        int updatedRows = jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement(INSERT_SQL);
            preparedStatement.setString(1, result.getDecisionId());
            preparedStatement.setString(2, input.orderNr());
            preparedStatement.setString(3, input.clientNr());
            preparedStatement.setString(4, input.deliveryCountry());
            preparedStatement.setString(5, input.deliveryAddress());
            preparedStatement.setInt(6, input.weightKg());
            preparedStatement.setString(7, input.phone());
            preparedStatement.setString(8, input.mail());
            preparedStatement.setString(9, result.getOutcome().name());
            preparedStatement.setBoolean(10, result.isAutoContinue());
            preparedStatement.setString(11, result.getRecommendedChannel().name());
            preparedStatement.setString(12, result.getRuleId());
            preparedStatement.setString(13, result.getRuleVersion());
            preparedStatement.setString(14, result.getReason());
            preparedStatement.setString(15, toPayloadJson(input));
            return preparedStatement;
        });

        log.info("Inserted shipping_decision_log row: decisionId={}, rows={}", result.getDecisionId(), updatedRows);
    }

    private String toPayloadJson(DecisionInput input) {
        Map<String, Object> payload = Map.of(
                "orderNr", input.orderNr(),
                "clientNr", input.clientNr(),
                "deliveryCountry", input.deliveryCountry(),
                "deliveryAddress", input.deliveryAddress(),
                "weightKg", input.weightKg(),
                "phone", input.phone(),
                "mail", input.mail()
        );
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize decision payload_json", e);
        }
    }
}
