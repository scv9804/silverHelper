package com.silverhelper.pipeline.config;

import com.silverhelper.pipeline.service.PipelineRunner;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 실행 방법.
 * <ul>
 *   <li>스케줄: pipeline.schedule.enabled=true면 매일 pipeline.schedule.cron(기본 04:00, Asia/Seoul)</li>
 *   <li>수동: pipeline.run-on-startup=true로 앱을 띄우면 시작 직후 1회 실행 (PR 본문 참고)</li>
 * </ul>
 */
public class PipelineScheduleConfig {

	private PipelineScheduleConfig() {
	}

	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	@ConditionalOnProperty(prefix = "pipeline.schedule", name = "enabled", havingValue = "true")
	static class Daily {

		private final PipelineRunner runner;

		Daily(PipelineRunner runner) {
			this.runner = runner;
		}

		@Scheduled(cron = "${pipeline.schedule.cron:0 0 4 * * *}", zone = "Asia/Seoul")
		void run() {
			runner.runAll();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "pipeline", name = "run-on-startup", havingValue = "true")
	static class OnStartup {

		@Bean
		ApplicationRunner pipelineStartupRunner(PipelineRunner runner) {
			return args -> runner.runAll();
		}

	}

}
