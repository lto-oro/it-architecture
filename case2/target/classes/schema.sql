CREATE TABLE IF NOT EXISTS shipping_decision_log (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    order_nr VARCHAR(128) NOT NULL,
    client_nr VARCHAR(128) NOT NULL,
    delivery_country VARCHAR(2) NOT NULL,
    delivery_address TEXT NOT NULL,
    weight_kg INT NOT NULL,
    phone VARCHAR(64) NOT NULL,
    mail VARCHAR(256) NOT NULL,
    outcome VARCHAR(64) NOT NULL,
    auto_continue BOOLEAN NOT NULL,
    recommended_channel VARCHAR(64) NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    rule_version VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    payload_json TEXT NOT NULL
);
