package com.silverhelper.chat.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 키가 있을 때만 구성되는 OpenAI 어댑터. 원문 질문이나 답변은 저장하지 않는다. */
public final class OpenAiModels implements EmbeddingModel, AnswerModel {
    private final RestClient client;
    private final JsonMapper json;
    public OpenAiModels(String key, JsonMapper json) {
        this.client=RestClient.builder().baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization","Bearer "+key).build();
        this.json=json;
    }
    @Override public String name() { return "text-embedding-3-small"; }
    @Override public boolean mock() { return false; }
    @Override public float[] embed(String text) {
        String response=client.post().uri("/embeddings").body(Map.of("model",name(),"input",text))
                .retrieve().body(String.class);
        var node=json.readTree(response).path("data").get(0).path("embedding");
        if (node.size()!=1536) throw new IllegalStateException("임베딩 차원이 1536이 아닙니다");
        float[] vector=new float[1536];
        for (int i=0;i<vector.length;i++) vector[i]=(float)node.get(i).asDouble();
        return vector;
    }
    @Override public String answer(String question,List<String> evidence) {
        if (evidence.isEmpty()) return "모름";
        String prompt="제공된 공개 복지 혜택 근거에 명시된 사실만 답하세요. 근거에 없는 정책, 자격, 금액은 만들지 마세요. "
                +"질문과 무관하거나 근거가 부족하면 정확히 '모름'이라고 답하세요.\n근거:\n"
                +String.join("\n",evidence)+"\n질문: "+question;
        String response=client.post().uri("/chat/completions").body(Map.of("model","gpt-4.1-mini",
                "temperature",0,"messages",List.of(Map.of("role","user","content",prompt))))
                .retrieve().body(String.class);
        String answer=json.readTree(response).path("choices").get(0).path("message").path("content").asString();
        return answer==null || answer.isBlank()?"모름":answer;
    }
}
