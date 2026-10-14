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
    @Test void 발송_실패를_기록해_재시도할_수_있다() {
        var repository=mock(NotificationRepository.class); var mail=mock(JavaMailSender.class);
        when(repository.candidates(any())).thenReturn(List.of(candidate()));
        when(repository.reserve(any(),anyString(),anyString())).thenReturn(42L);
        doThrow(new IllegalStateException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        new NotificationService(repository,mail,CLOCK,"sender@example.com").deliverDaily();
        verify(repository).failed(42L,"SMTP unavailable");
        verify(repository,never()).sent(42L);
    }
}
