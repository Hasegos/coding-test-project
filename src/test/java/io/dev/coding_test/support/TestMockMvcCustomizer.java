package io.dev.coding_test.support;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 테스트용 MockMvc 설정 — 모든 요청에 유효한 CSRF 토큰을 붙이고, {@link TestLoginContext}의 회원으로 로그인한다.
 * <p>
 * 기능 테스트가 CSRF 토큰·로그인 처리와 무관하게 동작을 검증할 수 있도록 한다.
 * CSRF 차단과 로그인 흐름 자체는 {@code SecurityConfigTest}, {@code AuthControllerTest}가 이 설정 없이 별도의 MockMvc로 검증한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TestMockMvcCustomizer implements MockMvcBuilderCustomizer {

    private final TestLoginContext testLoginContext;

    @Override
    public void customize(ConfigurableMockMvcBuilder<?> builder) {
        builder.defaultRequest(get("/").with(csrf()).with(testLoginContext::apply));
    }
}
