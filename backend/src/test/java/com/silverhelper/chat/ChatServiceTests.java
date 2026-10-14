package com.silverhelper.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.silverhelper.chat.dto.ChatDtos;
import com.silverhelper.chat.repository.RagRepository;
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
}
