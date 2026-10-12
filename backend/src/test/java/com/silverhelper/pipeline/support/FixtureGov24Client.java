package com.silverhelper.pipeline.support;

import com.silverhelper.pipeline.client.Gov24Client;
import com.silverhelper.pipeline.client.Gov24JsonParser;
import com.silverhelper.pipeline.client.SourcePage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** 픽스처 JSON으로 응답하는 정부24 클라이언트 (네트워크 없음). 응답은 실제 파서를 거친다 */
public class FixtureGov24Client implements Gov24Client {

	private final JsonMapper jsonMapper;
	private String listJson;
	private String conditionsJson;
	public final AtomicInteger calls = new AtomicInteger();
	public final AtomicInteger singleConditionCalls = new AtomicInteger();

	public FixtureGov24Client(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
		reset();
	}

	public void reset() {
		listJson = Fixtures.text("gov24-list.json");
		conditionsJson = Fixtures.text("gov24-conditions.json");
		calls.set(0);
		singleConditionCalls.set(0);
	}

	public String list() {
		return listJson;
	}

	public void setList(String json) {
		this.listJson = json;
	}

	@Override
	public SourcePage serviceList(int page, int perPage) {
		calls.incrementAndGet();
		return page(listJson, page);
	}

	@Override
	public SourcePage supportConditions(int page, int perPage) {
		calls.incrementAndGet();
		return page(conditionsJson, page);
	}

	@Override
	public SourcePage supportConditionsOf(String serviceId) {
		calls.incrementAndGet();
		singleConditionCalls.incrementAndGet();
		ObjectNode root = (ObjectNode) jsonMapper.readTree(conditionsJson);
		ArrayNode filtered = jsonMapper.createArrayNode();
		for (JsonNode item : root.get("data")) {
			if (serviceId.equals(item.path("서비스ID").asString())) {
				filtered.add(item);
			}
		}
		root.set("data", filtered);
		root.put("matchCount", filtered.size());
		return Gov24JsonParser.parse(jsonMapper, 200, root.toString());
	}

	private SourcePage page(String json, int page) {
		ObjectNode root = (ObjectNode) jsonMapper.readTree(json);
		if (page > 1) {
			root.set("data", jsonMapper.createArrayNode());
		}
		// 픽스처는 목록을 3건으로 줄였으므로 matchCount를 실제 건수로 맞춘다
		root.put("matchCount", page > 1 ? root.get("matchCount").asInt() : root.get("data").size());
		SourcePage parsed = Gov24JsonParser.parse(jsonMapper, 200, root.toString());
		return page > 1 ? new SourcePage(parsed.totalCount(), List.of()) : parsed;
	}

}
