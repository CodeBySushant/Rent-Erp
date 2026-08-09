-- ============================================================
-- V2: db-scheduler task table
--
-- Required by com.github.kagkarlsson:db-scheduler.
-- Flyway creates this before db-scheduler initialises so the
-- library can find the table on first startup.
-- ============================================================

CREATE TABLE scheduled_tasks (
    task_name           TEXT        NOT NULL,
    task_instance       TEXT        NOT NULL,
    task_data           BYTEA,
    execution_time      TIMESTAMPTZ NOT NULL,
    picked              BOOLEAN     NOT NULL DEFAULT FALSE,
    picked_by           TEXT,
    last_success        TIMESTAMPTZ,
    last_failure        TIMESTAMPTZ,
    consecutive_failures INTEGER,
    last_heartbeat      TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX idx_scheduled_tasks_exec ON scheduled_tasks (execution_time, picked);
