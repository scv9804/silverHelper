package com.silverhelper.pipeline.performance;

import com.silverhelper.pipeline.domain.RawKind;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.service.ContentHasher;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** HASH_AUDIT=1일 때만 실 DB를 읽는다. 데이터를 수정하거나 AI를 호출하지 않는다. */
class ContentHashAuditTests {
    private static final TypeReference<Map<String,Object>> MAP = new TypeReference<>() { };

    @Test
    void 배포_전_화이트리스트_해시_영향을_계산한다() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("HASH_AUDIT")));
        String url=System.getenv().getOrDefault("HASH_AUDIT_URL","jdbc:postgresql://localhost:5432/silverhelper");
        String user=System.getenv().getOrDefault("HASH_AUDIT_USER","silverhelper");
        String password=System.getenv("HASH_AUDIT_PASSWORD");
        if (password == null) throw new IllegalStateException("HASH_AUDIT_PASSWORD 환경변수가 필요합니다");
        JsonMapper json=new JsonMapper();
        int total=0, listChanged=0, inputChanged=0, verifiedAffected=0, pendingAffected=0;
        int legacyListWouldChange=0, legacyInputWouldChange=0, legacyVerifiedWouldChange=0;
        try (var connection=DriverManager.getConnection(url,user,password)) {
            connection.setReadOnly(true);
            try (var statement=connection.prepareStatement("""
                    select b.source,b.content_hash,b.verified_hash,l.content_hash,l.payload::text,
                           d.content_hash,d.payload::text
                      from benefit b
                      left join lateral (select content_hash,payload from benefit_raw
                          where benefit_id=b.id and kind='LIST' order by last_seen_at desc,id desc limit 1) l on true
                      left join lateral (select content_hash,payload from benefit_raw
                          where benefit_id=b.id and kind='DETAIL' order by last_seen_at desc,id desc limit 1) d on true
                     where b.status='ACTIVE'
                    """)) {
                statement.setFetchSize(200);
                try (var rows=statement.executeQuery()) {
                    while (rows.next()) {
                        total++;
                        Source source=Source.valueOf(rows.getString(1));
                        String current=rows.getString(2);
                        String verified=rows.getString(3);
                        String oldList=rows.getString(4);
                        String listJson=rows.getString(5);
                        String oldDetail=rows.getString(6);
                        String detailJson=rows.getString(7);
                        if (listJson != null) {
                            Map<String,Object> list=json.readValue(listJson,MAP);
                            String newList=ContentHasher.hash(source,RawKind.LIST,list);
                            if (!newList.equals(oldList)) listChanged++;
                            if (!newList.equals(ContentHasher.hash(list))) legacyListWouldChange++;
                        }
                        String inputJson=source==Source.GOV24 ? listJson : detailJson;
                        String oldInput=source==Source.GOV24 ? oldList : oldDetail;
                        if (inputJson == null) continue;
                        Map<String,Object> input=json.readValue(inputJson,MAP);
                        String newInput=ContentHasher.hash(source,source==Source.GOV24 ? RawKind.LIST : RawKind.DETAIL,input);
                        if (!newInput.equals(ContentHasher.hash(input))) {
                            legacyInputWouldChange++;
                            if (verified != null) legacyVerifiedWouldChange++;
                        }
                        if (!newInput.equals(oldInput)) {
                            inputChanged++;
                            if (verified != null) verifiedAffected++;
                            if (verified != null && !verified.equals(current)) pendingAffected++;
                        }
                    }
                }
            }
        }
        System.out.printf("HASH_AUDIT active=%d storedListMismatch=%d storedInputMismatch=%d verifiedAffected=%d alreadyPending=%d legacyListWouldChange=%d legacyInputWouldChange=%d legacyVerifiedWouldChange=%d%n",
                total,listChanged,inputChanged,verifiedAffected,pendingAffected,legacyListWouldChange,legacyInputWouldChange,legacyVerifiedWouldChange);
    }
}
