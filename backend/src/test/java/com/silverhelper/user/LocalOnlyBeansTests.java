package com.silverhelper.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.pipeline.controller.LocalSampleSeedController;
import com.silverhelper.pipeline.service.LocalSampleSeed;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalOnlyBeansTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalAdminLoginController.class, LocalSampleSeed.class,
                    LocalSampleSeedController.class);

    @Test
    void 운영_프로필에는_관리자_로그인과_샘플_시드_빈이_없다() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LocalAdminLoginController.class);
            assertThat(context).doesNotHaveBean(LocalSampleSeed.class);
            assertThat(context).doesNotHaveBean(LocalSampleSeedController.class);
        });
    }

    @Test
    void 로컬과_운영_프로필이_함께_켜져도_개발용_빈이_없다() {
        runner.withPropertyValues("spring.profiles.active=local,prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LocalAdminLoginController.class);
            assertThat(context).doesNotHaveBean(LocalSampleSeed.class);
            assertThat(context).doesNotHaveBean(LocalSampleSeedController.class);
        });
    }
}
