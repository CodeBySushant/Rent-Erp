package com.renterp.domain.billing.service;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.renterp.domain.billing.dto.CreateBillingRunRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Async billing worker (B15). A large run is persisted DRAFT + a RUNNING progress row, then
 * a db-scheduler one-time task is enqueued; the worker builds the bills off the request thread
 * and updates {@code billing_run_progress} as it goes, which the UI polls (~2s). On success the
 * progress flips COMPLETED; on failure it flips FAILED with the error, leaving the run DRAFT so
 * it can be cancelled/regenerated.
 *
 * <p>The heavy lifting lives in {@link BillingRunService#buildAsync} (a proxied, transactional
 * call so bills commit atomically); this service only orchestrates enqueue + success/failure
 * bookkeeping. {@link BillingRunService} is injected lazily to break the construction cycle
 * (BillingRunService → this → BillingRunService), and the scheduler lazily to break
 * Scheduler → Task bean → this → Scheduler.
 */
@Service
public class BillingAsyncService {

    private static final Logger log = LogManager.getLogger(BillingAsyncService.class);

    public static final String TASK_NAME = "billing-run-generate";
    // Small delay so the request transaction (run + progress rows) is committed before a
    // worker picks the task up — db-scheduler schedules on its own connection, not this tx.
    private static final int ENQUEUE_DELAY_SECONDS = 2;

    private final SchedulerClient scheduler;
    private final BillingRunService billingRunService;

    public BillingAsyncService(@Lazy SchedulerClient scheduler,
                               @Lazy BillingRunService billingRunService) {
        this.scheduler = scheduler;
        this.billingRunService = billingRunService;
    }

    /** db-scheduler task payload — Serializable so it round-trips through scheduled_tasks. */
    public record BillingRunTaskData(UUID runId, UUID propertyId,
                                     CreateBillingRunRequest request) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public void enqueue(UUID runId, UUID propertyId, CreateBillingRunRequest req) {
        BillingRunTaskData data = new BillingRunTaskData(runId, propertyId, req);
        TaskInstance<BillingRunTaskData> instance = new TaskInstance<>(TASK_NAME, runId.toString(), data);
        scheduler.schedule(instance, Instant.now().plusSeconds(ENQUEUE_DELAY_SECONDS));
    }

    /** Invoked by the db-scheduler task handler on a worker thread. */
    public void process(BillingRunTaskData data) {
        log.info("Async billing worker started — run: {}", data.runId());
        try {
            billingRunService.buildAsync(data.runId(), data.propertyId(), data.request());
            log.info("Async billing worker completed — run: {}", data.runId());
        } catch (Exception e) {
            log.error("Async billing worker failed — run: {}: {}", data.runId(), e.getMessage(), e);
            billingRunService.markAsyncFailed(data.runId(), e.getMessage());
        }
    }
}
