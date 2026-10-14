package com.silverhelper.chat.service;

import com.silverhelper.chat.dto.ChatDtos;
import com.silverhelper.chat.repository.RagRepository;
import com.silverhelper.chat.repository.RagRepository.Hit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class ChatService {
    private final RagRepository repository;
    private final EmbeddingModel embedding;
    private final AnswerModel answer;
    public ChatService(RagRepository repository,@Qualifier("embeddingModel") EmbeddingModel embedding,
            @Qualifier("answerModel") AnswerModel answer) {
        this.repository=repository; this.embedding=embedding; this.answer=answer;
    }

    @Scheduled(fixedDelay=3600000)
    public synchronized void index() { indexMissing(200); }

    private void indexMissing(int limit) {
        for (var doc:repository.missing(embedding.name(),limit)) {
            if (doc.content().isBlank()) {
                // 완료 표식도 검증 해시에 묶어, 원문이 바뀌면 다시 색인한다.
                repository.save(doc,embedding.name(),-1,"",new float[1536]);
                continue;
            }
            float[] marker=null;
            int chunk=0;
            for (int start=0;start<doc.content().length();start+=1800) {
                String content=doc.content().substring(start,Math.min(start+1800,doc.content().length()));
                float[] vector=embedding.embed(content);
                repository.save(doc,embedding.name(),chunk++,content,vector);
                marker=vector;
            }
            repository.save(doc,embedding.name(),-1,"",marker);
        }
    }

    public ChatDtos.Answer ask(String question) {
        List<Hit> hits=repository.search(embedding.name(),embedding.embed(question));
        List<Hit> relevant=hits.stream().filter(h->h.distance() < (embedding.mock()?0.85:0.65))
                .filter(h->overlaps(question,h.content())).limit(4).toList();
        if (relevant.isEmpty()) return new ChatDtos.Answer("모름",List.of(),answer.mock());
        List<String> evidence=new ArrayList<>();
        Set<ChatDtos.Source> sources=new LinkedHashSet<>();
        for (Hit h:relevant) {
            sources.add(h.source());
            evidence.add("혜택 #"+h.source().benefitId()+" "+h.source().name()+": "+h.content());
        }
        String result=answer.answer(question,evidence);
        if ("모름".equals(result.trim())) return new ChatDtos.Answer("모름",List.of(),answer.mock());
        return new ChatDtos.Answer(result,List.copyOf(sources),answer.mock());
    }

    static boolean overlaps(String question,String content) {
        String normalized=content.toLowerCase(Locale.ROOT);
        for (String token:question.toLowerCase(Locale.ROOT).split("[^가-힣a-z0-9]+")) {
            if (token.length()>=2 && normalized.contains(token)) return true;
        }
        return false;
    }
}
