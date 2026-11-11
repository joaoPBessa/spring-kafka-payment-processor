package com.joaopbessa.payments.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.support.SimpleTriggerContext;

@DisplayName("Dead letter replay scheduler")
class DeadLetterReplaySchedulerTest {

    private static final Instant MIDNIGHT_UTC = Instant.parse("2026-01-10T00:00:00Z");

    private final DeadLetterReplayService replayService = mock(DeadLetterReplayService.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DeadLetterReplayService.class, () -> replayService)
            .withUserConfiguration(DeadLetterReplayScheduler.class);

    private CronTask scheduledCronTask(ScheduledAnnotationBeanPostProcessor processor) {
        List<ScheduledTask> tasks = List.copyOf(processor.getScheduledTasks());
        assertThat(tasks).hasSize(1);
        return (CronTask) tasks.get(0).getTask();
    }

    private Instant nextRun(CronTask task) {
        SimpleTriggerContext context = new SimpleTriggerContext(Clock.fixed(MIDNIGHT_UTC, ZoneOffset.UTC));
        return task.getTrigger().nextExecution(context);
    }

    @Test
    @DisplayName("Should run once a day at the configured time and zone")
    void shouldRunOnceADayAtTheConfiguredTimeAndZone() {
        runner.withPropertyValues("app.dlt-replay.cron=0 0 3 * * *", "app.dlt-replay.zone=America/Sao_Paulo")
                .run(context -> {
                    CronTask task = scheduledCronTask(context.getBean(ScheduledAnnotationBeanPostProcessor.class));

                    assertThat(task.getExpression()).isEqualTo("0 0 3 * * *");
                    assertThat(nextRun(task)).isEqualTo(Instant.parse("2026-01-10T06:00:00Z"));
                });
    }

    @Test
    @DisplayName("Should run at the configured time in UTC when the zone is UTC")
    void shouldRunAtTheConfiguredTimeInUtcWhenTheZoneIsUtc() {
        runner.withPropertyValues("app.dlt-replay.cron=0 0 3 * * *", "app.dlt-replay.zone=UTC")
                .run(context -> assertThat(nextRun(
                        scheduledCronTask(context.getBean(ScheduledAnnotationBeanPostProcessor.class))))
                        .isEqualTo(Instant.parse("2026-01-10T03:00:00Z")));
    }

    @Test
    @DisplayName("Should not schedule anything when the replay is disabled")
    void shouldNotScheduleAnythingWhenTheReplayIsDisabled() {
        runner.withPropertyValues("app.dlt-replay.enabled=false", "app.dlt-replay.cron=0 0 3 * * *",
                        "app.dlt-replay.zone=UTC")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DeadLetterReplayScheduler.class);
                    assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    @Test
    @DisplayName("Should replay the backlog when it fires")
    void shouldReplayTheBacklogWhenItFires() {
        new DeadLetterReplayScheduler(replayService).replayPendingDeadLetters();

        verify(replayService, times(1)).replayPending();
    }

    @Test
    @DisplayName("Should survive a failed run so the next day still runs")
    void shouldSurviveAFailedRunSoTheNextDayStillRuns() {
        when(replayService.replayPending()).thenThrow(new DeadLetterReadException("down", new RuntimeException()));
        DeadLetterReplayScheduler scheduler = new DeadLetterReplayScheduler(replayService);

        scheduler.replayPendingDeadLetters();
        scheduler.replayPendingDeadLetters();

        verify(replayService, times(2)).replayPending();
    }

}
