package com.silverhelper.chat.service;

import java.util.List;

/** 외부 키가 없는 환경의 검색 실습용 모델. 답변은 정책 사실을 만들어 내지 않는다. */
public final class DeterministicModels implements EmbeddingModel, AnswerModel {
    @Override public String name() { return "deterministic-1536-v1"; }
    @Override public boolean mock() { return true; }
    @Override public float[] embed(String text) {
        float[] vector=new float[1536];
        String normalized=text.toLowerCase().replaceAll("[^가-힣a-z0-9]", " ");
        for (String word: normalized.split("\\s+")) {
            if (word.length()<2) continue;
            for (int i=0;i<word.length()-1;i++) vector[Math.floorMod(word.substring(i,i+2).hashCode(),vector.length)]++;
        }
        double norm=0;
        for (float v:vector) norm+=v*v;
        if (norm>0) for (int i=0;i<vector.length;i++) vector[i]/=(float)Math.sqrt(norm);
        return vector;
    }
    @Override public String answer(String question, List<String> evidence) {
        return evidence.isEmpty()?"모름":"모의 응답: 검색된 공개 혜택의 공식 출처를 확인하세요. 실제 자격 판단은 제공하지 않습니다.";
    }
}
