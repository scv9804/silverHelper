package com.silverhelper.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 Clock을 주입받아 쓴다 (테스트에서 고정 시각으로 바꾸기 위해). 기준 시간대는 Asia/Seoul.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	Clock clock() {
		return Clock.system(ZONE);
	}

}
