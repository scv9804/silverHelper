package com.silverhelper.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverhelper.chat.repository.RagRepository;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class RagRepositoryTests {
    @Test
    @SuppressWarnings("unchecked")
    void 새_색인은_검증_해시의_원문만_사용한다() throws Exception {
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        var rs=mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(7L);
        when(rs.getString(2)).thenReturn("verified-hash");
        when(rs.getString(3)).thenReturn("NATIONAL");
        when(rs.getString(4)).thenReturn("{\"servNm\":\"검증된 이름\",\"tgtrDtlCn\":\"검증된 대상\"}");
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class))).thenAnswer(call ->
                List.of(((RowMapper<RagRepository.Document>)call.getArgument(2)).mapRow(rs,0)));

        var docs=new RagRepository(jdbc,new JsonMapper()).missing("test-model",10);

        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().hash()).isEqualTo("verified-hash");
        assertThat(docs.getFirst().content()).contains("검증된 이름","검증된 대상");
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(),any(Map.class),any(RowMapper.class));
        assertThat(sql.getValue()).contains("raw.content_hash=b.verified_hash", "raw.payload::text");
        assertThat(sql.getValue()).doesNotContain("b.target_content", "b.summary");
    }

    @Test
    void 검색은_기존_검증_임베딩을_업데이트_대기_중에도_유지한다() {
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        new RagRepository(jdbc,new JsonMapper()).search("test-model",new float[]{1});
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(),any(Map.class),any(RowMapper.class));
        assertThat(sql.getValue()).contains("r.verified_hash=b.verified_hash", "b.content_hash <> b.verified_hash update_pending");
        assertThat(sql.getValue()).doesNotContain("b.content_hash=b.verified_hash");
    }
}
