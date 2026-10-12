package com.silverhelper.pipeline.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** 테스트용 시계. 재수집 사이에 시간을 흘려 "나중에 받은 원문"을 구분한다 */
public class MutableClock extends Clock {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	public static final Instant START = ZonedDateTime.of(2026, 10, 13, 4, 0, 0, 0, ZONE).toInstant();

	private volatile Instant now = START;

	public void reset() {
		now = START;
	}

	public void advance(Duration d) {
		now = now.plus(d);
	}

	@Override
	public ZoneId getZone() {
		return ZONE;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(now, zone);
	}

	@Override
	public Instant instant() {
		return now;
	}

}
