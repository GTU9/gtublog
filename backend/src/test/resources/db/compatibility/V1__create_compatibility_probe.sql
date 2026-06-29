CREATE TABLE phase0_compatibility_probe (
    id BIGINT PRIMARY KEY,
    probe_value VARCHAR(64) NOT NULL
);

INSERT INTO phase0_compatibility_probe (id, probe_value)
VALUES (1, 'flyway-connected');
