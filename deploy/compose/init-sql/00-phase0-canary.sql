-- Executed by the oceanbase-ce image after tenant bootstrap (mounted at /root/boot/init.d).
-- Phase 0 canary only; real schemas (payments, ledger, outbox) arrive in Phase 1 migrations.
CREATE TABLE IF NOT EXISTS phase0_canary (
    id INT PRIMARY KEY,
    note VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO phase0_canary (id, note) VALUES (1, 'oceanbase bootstrapped')
ON DUPLICATE KEY UPDATE note = VALUES(note);
