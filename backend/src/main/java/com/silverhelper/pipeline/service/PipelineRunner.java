package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.config.PipelineProperties;
import com.silverhelper.pipeline.domain.Source;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 파이프라인 1회 실행: 원천별 수집 → AI 추출·교차검증.
 * 스케줄(PipelineScheduleConfig)과 시작 시 실행(pipeline.run-on-startup)이 이것을 부른다. 같은 프로세스에서 동시에 두 번 돌지 않는다.
 */
@Service
public class PipelineRunner {

	private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

	public record RunReport(boolean skipped, List<CollectService.Report> collects, ExtractionService.Report extraction) {
	}

	private final CollectService collectService;
	private final ExtractionService extractionService;
	private final PipelineProperties properties;
	private final AtomicBoolean running = new AtomicBoolean();

	public PipelineRunner(CollectService collectService, ExtractionService extractionService, PipelineProperties properties) {
		this.collectService = collectService;
		this.extractionService = extractionService;
		this.properties = properties;
	}

	public RunReport runAll() {
		return run(properties.sources());
	}

	public RunReport run(Collection<Source> sources) {
		if (!running.compareAndSet(false, true)) {
			log.warn("파이프라인이 이미 실행 중이라 건너뜀");
			return new RunReport(true, List.of(), null);
		}
		try {
			log.info("파이프라인 시작: {}", sources);
			List<CollectService.Report> collects = new ArrayList<>();
			for (Source source : sources) {
				collects.add(collectService.collect(source));
			}
			ExtractionService.Report extraction = extractionService.processPending();
			return new RunReport(false, collects, extraction);
		}
		finally {
			running.set(false);
		}
	}

}
