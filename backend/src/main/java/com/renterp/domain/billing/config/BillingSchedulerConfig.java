package com.renterp.domain.billing.config;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.renterp.domain.billing.service.BillingAsyncService;
import com.renterp.domain.billing.service.BillingAsyncService.BillingRunTaskData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

/**
 * db-scheduler wiring for the async billing worker (B15).
 *
 * <p>Registers the one-time task that generates a large billing run off-thread, and an explicit
 * {@link Scheduler} bean (started here) so a {@code SchedulerClient} is always available for
 * {@link BillingAsyncService} to schedule against. The bean is {@code @ConditionalOnMissingBean}
 * so if the db-scheduler starter's own auto-configuration is active it wins; this is the
 * fallback that guarantees the scheduler exists.
 */
@Configuration
public class BillingSchedulerConfig {

    @Bean
    public Task<BillingRunTaskData> billingRunGenerateTask(BillingAsyncService asyncService) {
        return Tasks.oneTime(BillingAsyncService.TASK_NAME, BillingRunTaskData.class)
                .execute((instance, ctx) -> asyncService.process(instance.getData()));
    }

    /**
     * M7: the settings now come from application.yml ({@code db-scheduler.*}).
     * Before, this bean hard-coded 5 threads / 5 s polling while the yml said
     * 10 s, and the yml values were silently ignored.
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean(Scheduler.class)
    public Scheduler scheduler(DataSource dataSource, List<Task<?>> tasks,
                               @Value("${db-scheduler.threads:5}") int threads,
                               @Value("${db-scheduler.polling-interval:10s}") Duration pollingInterval,
                               @Value("${db-scheduler.table-name:scheduled_tasks}") String tableName,
                               @Value("${db-scheduler.shutdown-max-wait:30s}") Duration shutdownMaxWait,
                               @Value("${db-scheduler.immediate-execution-enabled:true}") boolean immediate) {
        var builder = Scheduler.create(dataSource, tasks.toArray(new Task<?>[0]))
                .threads(threads)
                .pollingInterval(pollingInterval)
                .tableName(tableName)
                .shutdownMaxWait(shutdownMaxWait);
        if (immediate) {
            builder = builder.enableImmediateExecution();
        }
        Scheduler scheduler = builder.build();
        scheduler.start();
        return scheduler;
    }
}
