package com.silverhelper.notification.service;

import com.silverhelper.benefit.domain.BenefitEligibility;
import com.silverhelper.benefit.dto.BenefitDtos.Profile;
import com.silverhelper.benefit.service.EligibilityMatcher;
import com.silverhelper.notification.dto.NotificationSettings;
import com.silverhelper.notification.repository.NotificationRepository;
import com.silverhelper.notification.repository.NotificationRepository.Candidate;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Logger log=LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository repository;
    private final JavaMailSender sender;
    private final Clock clock;
    private final String from;
    public NotificationService(NotificationRepository repository, JavaMailSender sender, Clock clock,
            @Value("${app.notification.from:}") String from) {
        this.repository=repository; this.sender=sender; this.clock=clock; this.from=from;
    }

    @Transactional(readOnly=true)
    public NotificationSettings get(long userId) { return repository.settings(userId); }

    @Transactional
    public NotificationSettings put(long userId, NotificationSettings settings) { return repository.save(userId, settings); }

    public void deliverDaily() {
        LocalDate today=LocalDate.now(clock);
        for (Candidate c: repository.candidates(today)) {
            if (!matches(c)) continue;
            if (c.newEnabled() && (today.equals(c.firstSeen()) || today.equals(c.verifiedDate())
                    || repository.retryable(c,"NEW_BENEFIT",c.firstSeen().toString())))
                send(c,"NEW_BENEFIT",c.firstSeen().toString(),"새 혜택");
            if (c.deadlineEnabled() && c.deadline()!=null && !c.deadline().isBefore(today)
                    && (!c.deadline().isAfter(today.plusDays(c.days()))
                        || repository.retryable(c,"DEADLINE",c.deadline().toString())))
                send(c,"DEADLINE",c.deadline().toString(),"마감 임박");
        }
    }

    private boolean matches(Candidate c) {
        BenefitEligibility e=new BenefitEligibility();
        e.minAge=c.minAge()==null?null:c.minAge().shortValue();
        e.maxAge=c.maxAge()==null?null:c.maxAge().shortValue();
        e.ltcRequired=c.ltcRequired(); e.ltcGrades=c.ltcGrades();
        e.incomeMaxMedianPct=c.incomeLimit()==null?null:c.incomeLimit().shortValue();
        e.requiresBasicLivelihood=c.requiresLivelihood(); e.requiresNearPoverty=c.requiresNearPoverty();
        e.requiresBasicPension=c.requiresPension(); e.requiresDisability=c.requiresDisability();
        e.requiresVeteran=c.requiresVeteran(); e.requiresSingleHousehold=c.requiresSingle();
        Profile p=new Profile(c.age(),c.ctpv(),c.sgg(),c.ltcGrade(),c.hasNoLtcGrade(),c.income(),
                c.livelihood(),c.nearPoverty(),c.pension(),c.disability(),c.veteran(),c.livingAlone());
        EligibilityMatcher.Result result=EligibilityMatcher.match(e,p,c.benefitCtpv(),c.benefitSgg());
        return !result.excluded() && result.checkItems().isEmpty();
    }

    private void send(Candidate c,String kind,String event,String label) {
        Long id=repository.reserve(c,kind,event);
        if (id==null) return;
        try {
            SimpleMailMessage message=new SimpleMailMessage();
            if (from!=null && !from.isBlank()) message.setFrom(from);
            message.setTo(c.email());
            message.setSubject("[실버헬퍼] " + label + ": " + c.benefitName());
            message.setText(c.profileName()+"님을 위한 " + label + " 혜택입니다.\n"
                    +c.benefitName()+"\n공식 출처: "+c.sourceUrl()+"\n기준일: "+LocalDate.now(clock));
            sender.send(message);
            repository.sent(id);
        } catch (RuntimeException ex) {
            // SMTP 예외도 수신 여부가 불확실하다. 운영자가 확인할 때까지 PENDING을 재시도하지 않는다.
            try {
                repository.recordUncertain(id,ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage());
            } catch (RuntimeException recordFailure) {
                log.error("알림 발송 불확실 상태 기록 실패 deliveryId={}",id,recordFailure);
            }
            log.warn("알림 발송 결과 확인 필요 deliveryId={}",id,ex);
        }
    }
}
