-- Executed by the oceanbase-ce image after tenant bootstrap (mounted at /root/boot/init.d).
-- NOTE: init.d only runs on first bootstrap of the ob-data volume. On an existing volume,
-- apply manually: docker compose exec oceanbase obclient -uroot@paylab -p... < this file.

-- Service-owned databases (schema-per-service; tables come from each service's Flyway).
CREATE DATABASE IF NOT EXISTS paylab_gateway;
CREATE DATABASE IF NOT EXISTS paylab_ledger;
CREATE DATABASE IF NOT EXISTS paylab_risk;

-- Phase 0 canary (health/gate check target).
CREATE TABLE IF NOT EXISTS phase0_canary (
    id INT PRIMARY KEY,
    note VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO phase0_canary (id, note) VALUES (1, 'oceanbase bootstrapped')
ON DUPLICATE KEY UPDATE note = VALUES(note);
