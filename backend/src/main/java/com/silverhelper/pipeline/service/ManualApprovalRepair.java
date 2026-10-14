package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.repository.EligibilityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 일회성 보정 절차. 운영자가 검토 기록과 원본을 대조해 수동 입력이 확실한 benefit ID를
 * APP_PIPELINE_MANUAL_APPROVAL_REPAIR_IDS=1,2 형태로 지정한 경우에만 실행한다.
 * 빈 목록은 아무것도 수정하지 않는다. 적용 건수를 로그로 남기고 다음 실행 전 변수를 제거한다.
 */
@Configuration(proxyBeanMethods=false)
public class ManualApprovalRepair {
    private static final Logger log=LoggerFactory.getLogger(ManualApprovalRepair.class);
    @Bean
    ApplicationRunner manualApprovalRepairRunner(EligibilityStore store,
            @Value("${app.pipeline.manual-approval-repair-ids:}") String ids) {
        return args -> {
            if (ids.isBlank()) return;
            int changed=0;
            for (String id:ids.split(",")) {
                changed+=store.markLegacyManualApproval(Long.parseLong(id.trim()));
            }
            log.info("기존 수동 승인 출처 보정: {}건",changed);
        };
    }
}
