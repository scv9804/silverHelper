package com.silverhelper.notification.config;

import com.silverhelper.notification.service.NotificationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
public class NotificationSchedule {
    private final NotificationService service;
    public NotificationSchedule(NotificationService service) { this.service=service; }
    @Scheduled(cron="0 0 9 * * *", zone="Asia/Seoul")
    public void daily() { service.deliverDaily(); }
}
