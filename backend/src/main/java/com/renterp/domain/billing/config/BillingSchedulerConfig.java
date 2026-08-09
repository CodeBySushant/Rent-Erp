package com.renterp.domain.billing.config;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.renterp.domain.billing.service.BillingAsyncService;
import com.renterp.domain.billing.service.BillingAsyncService.BillingRunTaskData;
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

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean(Scheduler.class)
    public Scheduler scheduler(DataSource dataSource, List<Task<?>> tasks) {
        Scheduler scheduler = Scheduler.create(dataSource, tasks.toArray(new Task<?>[0]))
                .threads(5)
                .pollingInterval(Duration.ofSeconds(5))
                .tableName("scheduled_tasks")
                .build();
        scheduler.start();
        return scheduler;
    }
}
