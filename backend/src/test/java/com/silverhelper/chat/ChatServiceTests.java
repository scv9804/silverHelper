package com.silverhelper.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.silverhelper.chat.dto.ChatDtos;
import com.silverhelper.chat.repository.RagRepository;
import com.silverhelper.chat.repository.RagRepository.Document;
import com.silverhelper.chat.repository.RagRepository.Hit;
import com.silverhelper.chat.service.ChatService;
import com.silverhelper.chat.service.DeterministicModels;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatServiceTests {
    @Test void 근거가_없으면_모름과_mock_표시() {
        RagRepository repository=Mockito.mock(RagRepository.class);
        when(repository.missing(anyString(),anyInt())).thenReturn(List.of());
        when(repository.search(anyString(),any())).thenReturn(List.of());
        ChatDtos.Answer answer=new ChatService(repository,new DeterministicModels(),new DeterministicModels()).ask("기초연금 지원?");
        assertThat(answer.answer()).isEqualTo("모름");
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.mock()).isTrue();
        verify(repository,never()).missing(anyString(),anyInt());
    }

    @Test void 근거가_있으면_출처와_기준을_반환한다() {
        RagRepository repository=Mockito.mock(RagRepository.class);
        when(repository.missing(anyString(),anyInt())).thenReturn(List.of());
        var basis=new ChatDtos.Basis(OffsetDateTime.parse("2026-10-14T09:00:00+09:00"),null,null,false);
        var source=new ChatDtos.Source(3,"기초연금","https://www.gov.kr/",basis);
        when(repository.search(anyString(),any())).thenReturn(List.of(new Hit("기초연금 지원",source,0.1)));
        ChatDtos.Answer answer=new ChatService(repository,new DeterministicModels(),new DeterministicModels()).ask("기초연금 지원?");
        assertThat(answer.mock()).isTrue();
        assertThat(answer.sources()).containsExactly(source);
        assertThat(answer.answer()).startsWith("모의 응답:");
    }

    @Test void 스케줄_색인에서만_누락_문서를_조회한다() {
        RagRepository repository=Mockito.mock(RagRepository.class);
        when(repository.missing(anyString(),anyInt())).thenReturn(List.of());
        new ChatService(repository,new DeterministicModels(),new DeterministicModels()).index();
        verify(repository).missing(anyString(),anyInt());
    }

    @Test void 빈_문서는_완료_표식을_남기고_임베딩을_호출하지_않는다() {
        RagRepository repository=Mockito.mock(RagRepository.class);
        var embedding=Mockito.mock(com.silverhelper.chat.service.EmbeddingModel.class);
        when(embedding.name()).thenReturn("test-model");
        Document empty=new Document(7L,"verified-hash","  ");
        when(repository.missing("test-model",200)).thenReturn(List.of(empty));

        new ChatService(repository,embedding,new DeterministicModels()).index();

        org.mockito.ArgumentCaptor<float[]> vector=org.mockito.ArgumentCaptor.forClass(float[].class);
        verify(repository).save(org.mockito.ArgumentMatchers.eq(empty),org.mockito.ArgumentMatchers.eq("test-model"),
                org.mockito.ArgumentMatchers.eq(-1),org.mockito.ArgumentMatchers.eq(""),vector.capture());
        assertThat(vector.getValue()).hasSize(1536).containsOnly(0f);
        verify(embedding,never()).embed(anyString());
    }
}
