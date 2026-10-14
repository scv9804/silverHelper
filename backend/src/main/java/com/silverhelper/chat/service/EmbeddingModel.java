package com.silverhelper.chat.service;

public interface EmbeddingModel {
    String name();
    boolean mock();
    float[] embed(String text);
}
