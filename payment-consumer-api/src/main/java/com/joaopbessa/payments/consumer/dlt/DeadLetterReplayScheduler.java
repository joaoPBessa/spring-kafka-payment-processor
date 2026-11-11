package com.joaopbessa.payments.consumer.dlt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.dlt-replay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterReplayScheduler {

	private final DeadLetterReplayService replayService;

	@Scheduled(cron = "${app.dlt-replay.cron}", zone = "${app.dlt-replay.zone}")
	public void replayPendingDeadLetters() {
		try {
			replayService.replayPending();
		} catch (RuntimeException e) {
			log.error("The scheduled dead letter replay failed", e);
		}
	}

}
