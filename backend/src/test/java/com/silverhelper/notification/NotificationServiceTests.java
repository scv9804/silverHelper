package com.silverhelper.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.silverhelper.notification.repository.NotificationRepository;
import com.silverhelper.notification.repository.NotificationRepository.Candidate;
import com.silverhelper.notification.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationServiceTests {
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"),ZoneId.of("Asia/Seoul"));
    private Candidate candidate() {
        return new Candidate(1,2,3,"test@example.com","부모","기초연금","https://www.gov.kr/",
                null,null,LocalDate.of(2026,10,14),null,null,7,true,false,75,"서울특별시",null,
                null,null,null,null,null,null,null,null,null,
                null,null,null,null,null,null,null,null,null,null,null);
    }
    @Test void 새_혜택을_한번_예약하고_보낸다() {
        var repository=mock(NotificationRepository.class); var mail=mock(JavaMailSender.class);
        when(repository.candidates(any())).thenReturn(List.of(candidate()));
        when(repository.reserve(any(),anyString(),anyString())).thenReturn(42L);
        new NotificationService(repository,mail,CLOCK,"sender@example.com").deliverDaily();
        verify(mail).send(any(SimpleMailMessage.class));
        verify(repository).sent(42L);
    }
    @Test void SMTP_예외는_수신_여부를_확인할_때까지_보류한다() {
        var repository=mock(NotificationRepository.class); var mail=mock(JavaMailSender.class);
        when(repository.candidates(any())).thenReturn(List.of(candidate()));
        when(repository.reserve(any(),anyString(),anyString())).thenReturn(42L);
        doThrow(new IllegalStateException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        new NotificationService(repository,mail,CLOCK,"sender@example.com").deliverDaily();
        verify(repository).recordUncertain(42L,"SMTP unavailable");
        verify(repository,never()).failed(anyLong(),anyString());
        verify(repository,never()).sent(42L);
    }
    @Test void SMTP_성공_후_상태_저장에_실패해도_재시도_상태로_바꾸지_않는다() {
        var repository=mock(NotificationRepository.class); var mail=mock(JavaMailSender.class);
        when(repository.candidates(any())).thenReturn(List.of(candidate()));
        when(repository.reserve(any(),anyString(),anyString())).thenReturn(42L);
        doThrow(new IllegalStateException("DB unavailable")).when(repository).sent(42L);
        new NotificationService(repository,mail,CLOCK,"sender@example.com").deliverDaily();
        verify(mail).send(any(SimpleMailMessage.class));
        verify(repository).recordUncertain(42L,"DB unavailable");
        verify(repository,never()).failed(anyLong(),anyString());
    }
}
