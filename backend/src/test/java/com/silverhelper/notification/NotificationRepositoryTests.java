package com.silverhelper.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.silverhelper.notification.repository.NotificationRepository;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class NotificationRepositoryTests {
    @Test
    void 미확인_PENDING은_후보_조회에서_자동_재시도하지_않는다() {
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        new NotificationRepository(jdbc).candidates(LocalDate.of(2026,10,14));
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(),any(Map.class),any(RowMapper.class));
        assertThat(sql.getValue()).contains("d.status='FAILED'").doesNotContain("d.status='PENDING'");
    }
}
