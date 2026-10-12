package com.silverhelper.pipeline.support;

import com.silverhelper.pipeline.ai.AiOutput;
import com.silverhelper.pipeline.ai.EligibilityExtractor;
import com.silverhelper.pipeline.ai.RuleBasedEligibilityExtractor;
import com.silverhelper.pipeline.ai.SourceText;
import com.silverhelper.pipeline.domain.AiProvider;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import tools.jackson.databind.json.JsonMapper;

/**
 * 테스트용 추출기. 기본은 규칙 기반 가짜와 같고, script를 주면 그 문자열을 모델 응답으로 돌려준다.
 * 호출 수를 세서 "같은 원문이면 AI를 다시 부르지 않음"을 확인한다.
 */
public class ScriptedExtractor implements EligibilityExtractor {

	private final AiProvider provider;
	private final RuleBasedEligibilityExtractor rules;
	private Function<SourceText, String> script;
	public final AtomicInteger calls = new AtomicInteger();

	public ScriptedExtractor(AiProvider provider, JsonMapper jsonMapper) {
		this.provider = provider;
		this.rules = new RuleBasedEligibilityExtractor(provider, jsonMapper);
	}

	public void reset() {
		script = null;
		calls.set(0);
	}

	public void respond(Function<SourceText, String> script) {
		this.script = script;
	}

	@Override
	public AiProvider provider() {
		return provider;
	}

	@Override
	public String promptVersion() {
		return "test-v1";
	}

	@Override
	public AiOutput extract(SourceText text) {
		calls.incrementAndGet();
		if (script == null) {
			AiOutput out = rules.extract(text);
			return new AiOutput("scripted-" + provider.name().toLowerCase(), out.content(), 10, 5);
		}
		return new AiOutput("scripted-" + provider.name().toLowerCase(), script.apply(text), 10, 5);
	}

}
