CREATE TABLE IF NOT EXISTS fact_wireless_outage_timeline (
    outage_id BIGINT PRIMARY KEY,
    event_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    severity INTEGER NOT NULL,
    asset_id BIGINT NOT NULL,
    active INTEGER NOT NULL,
    domain_id VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS outage_asset (
    asset_id BIGINT PRIMARY KEY,
    asset_name VARCHAR(128) NOT NULL,
    active INTEGER NOT NULL,
    domain_id VARCHAR(64) NOT NULL
);

TRUNCATE TABLE outage_asset, fact_wireless_outage_timeline;

INSERT INTO outage_asset(asset_id, asset_name, active, domain_id) VALUES
    (101, 'asset-101', 1, 'test-domain'),
    (102, 'inactive-asset', 0, 'test-domain'),
    (103, 'other-domain-asset', 1, 'other-domain');

INSERT INTO fact_wireless_outage_timeline
    (outage_id, event_date, status, severity, asset_id, active, domain_id) VALUES
    (1, '2026-11-13', 'OPEN', 2, 101, 1, 'test-domain'),
    (2, '2026-11-12', 'CLOSED', 1, 102, 0, 'test-domain'),
    (3, '2026-11-11', 'OPEN', 3, 103, 1, 'other-domain');
