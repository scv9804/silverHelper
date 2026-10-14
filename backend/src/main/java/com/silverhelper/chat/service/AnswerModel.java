package com.silverhelper.chat.service;

import java.util.List;

public interface AnswerModel {
    boolean mock();
    String answer(String question, List<String> evidence);
}
