package com.silverhelper.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthKeyConfigTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AuthKeyConfig.class)
            .withPropertyValues("spring.profiles.active=prod");

    @Test void 운영_프로필에서_JWT_키가_없으면_기동에_실패한다() {
        context.run(result -> assertThat(result.getStartupFailure())
                .hasRootCauseMessage("JWT_SECRET must be at least 32 bytes"));
    }

    @Test void 운영_프로필에서_JWT_키가_짧으면_기동에_실패한다() {
        context.withPropertyValues("app.auth.jwt-secret=short")
                .run(result -> assertThat(result.getStartupFailure())
                        .hasRootCauseMessage("JWT_SECRET must be at least 32 bytes"));
    }

    @Test void 운영_프로필에서_충분한_JWT_키로_기동한다() {
        context.withPropertyValues("app.auth.jwt-secret=0123456789abcdef0123456789abcdef")
                .run(result -> assertThat(result).hasNotFailed());
    }

    @Test void 운영과_로컬_복합_프로필에서_JWT_키가_없거나_짧으면_기동에_실패한다() {
        ApplicationContextRunner mixed = context.withPropertyValues("spring.profiles.active=prod,local");
        mixed.run(result -> assertThat(result.getStartupFailure())
                .hasRootCauseMessage("JWT_SECRET must be at least 32 bytes"));
        mixed.withPropertyValues("app.auth.jwt-secret=short")
                .run(result -> assertThat(result.getStartupFailure())
                        .hasRootCauseMessage("JWT_SECRET must be at least 32 bytes"));
    }

    @Test void 운영과_테스트_복합_프로필에서도_짧은_JWT_키를_거부한다() {
        context.withPropertyValues("spring.profiles.active=prod,test", "app.auth.jwt-secret=short")
                .run(result -> assertThat(result.getStartupFailure())
                        .hasRootCauseMessage("JWT_SECRET must be at least 32 bytes"));
    }
}
