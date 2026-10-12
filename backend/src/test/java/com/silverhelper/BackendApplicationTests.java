package com.silverhelper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		// 테스트에서는 실제 AI를 호출하지 않으므로 더미 키로 설정만 통과시킨다
		"spring.ai.anthropic.api-key=test-key",
		"spring.ai.openai.api-key=test-key"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
