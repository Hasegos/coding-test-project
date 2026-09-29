package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.InvalidFieldException;
import io.dev.coding_test.dto.setting.LlmConnectionTestRequest;
import io.dev.coding_test.dto.setting.LlmConnectionTestResponse;
import io.dev.coding_test.dto.setting.LlmSettingRequest;
import io.dev.coding_test.dto.setting.LlmSettingResponse;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.llm.exception.LlmAuthException;
import io.dev.coding_test.llm.exception.LlmException;
import io.dev.coding_test.model.enums.LlmProvider;
import io.dev.coding_test.repository.LlmSettingRepository;
import io.dev.coding_test.support.FakeLlmClient;
import io.dev.coding_test.support.FakeLlmClientFactory;
import io.dev.coding_test.support.TestLoginContext;
import io.dev.coding_test.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class LlmSettingServiceTest {

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private TestLoginContext testLoginContext;

    /** 로그인한 회원 (테스트마다 새로 가입) */
    private Long userId;

    @Autowired
    private LlmSettingService llmSettingService;

    @Autowired
    private LlmSettingRepository llmSettingRepository;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private FakeLlmClientFactory fakeLlmClientFactory;

    @BeforeEach
    void setUp() {
        userId = testUsers.login("tester").getUserId();
        fakeLlmClient.reset();
    }

    @AfterEach
    void resetLogin() {
        testLoginContext.reset();
    }

    @Test
    void 설정하지_않았으면_빈_값을_반환한다() {
        assertThat(llmSettingService.getSetting(userId)).isEmpty();
        assertThat(llmSettingService.findConnection(userId)).isEmpty();
        assertThat(llmSettingService.isConfigured(userId)).isFalse();
    }

    @Test
    void 접속_설정을_단일_행으로_저장하고_다시_저장하면_덮어쓴다() {
        llmSettingService.save(userId, request(LlmProvider.OLLAMA, " 100.100.0.10 ", 11434, " qwen2.5:7b ", null, false));
        LlmSettingResponse saved = llmSettingService.save(userId, 
                request(LlmProvider.LMSTUDIO, "100.100.0.99", 1234, "qwen2.5-vl-7b-instruct", null, false));

        assertThat(llmSettingRepository.count()).isEqualTo(1);
        assertThat(llmSettingRepository.findById(userId)).isPresent();
        assertThat(saved.provider()).isEqualTo(LlmProvider.LMSTUDIO);
        assertThat(saved.host()).isEqualTo("100.100.0.99");
        assertThat(saved.model()).isEqualTo("qwen2.5-vl-7b-instruct");
        assertThat(llmSettingService.isConfigured(userId)).isTrue();
    }

    @Test
    void 저장_시_앞뒤_공백을_제거한다() {
        LlmSettingResponse saved = llmSettingService.save(userId, 
                request(LlmProvider.OLLAMA, " 100.100.0.10 ", 11434, " qwen2.5:7b ", null, false));

        assertThat(saved.host()).isEqualTo("100.100.0.10");
        assertThat(saved.model()).isEqualTo("qwen2.5:7b");
    }

    @Test
    void API_Key는_비워두면_유지하고_삭제를_선택하면_지운다() {
        llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, "100.100.0.10", 1234, "model", "secret", false));

        LlmSettingResponse kept = llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, "100.100.0.10", 1234, "model", "", false));
        assertThat(kept.hasApiKey()).isTrue();
        assertThat(llmSettingService.findConnection(userId)).get().extracting(LlmConnection::apiKey).isEqualTo("secret");

        LlmSettingResponse cleared = llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, "100.100.0.10", 1234, "model", null, true));
        assertThat(cleared.hasApiKey()).isFalse();
    }

    @Test
    void 연결_테스트는_입력한_접속_정보로_모델_목록을_조회한다() {
        fakeLlmClient.willListModels(() -> Optional.of(List.of("qwen2.5-vl-7b-instruct")));

        LlmConnectionTestResponse result = llmSettingService.testConnection(userId, 
                new LlmConnectionTestRequest(LlmProvider.LMSTUDIO, "100.100.0.99", 1234, null));

        assertThat(result.ok()).isTrue();
        assertThat(result.models()).containsExactly("qwen2.5-vl-7b-instruct");
        assertThat(result.message()).isNull();
        assertThat(fakeLlmClientFactory.lastConnection().baseUrl()).isEqualTo("http://100.100.0.99:1234");
    }

    @Test
    void 연결_테스트_실패는_예외_대신_ok_false로_돌려준다() {
        fakeLlmClient.willListModels(() -> {
            throw new LlmException("LLM 서버 인증에 실패했어요. API Key를 확인해주세요.");
        });

        LlmConnectionTestResponse result = llmSettingService.testConnection(userId, 
                new LlmConnectionTestRequest(LlmProvider.LMSTUDIO, "100.100.0.99", 1234, null));

        assertThat(result.ok()).isFalse();
        assertThat(result.models()).isNull();
        assertThat(result.message()).isEqualTo("LLM 서버 인증에 실패했어요. API Key를 확인해주세요.");
    }

    @Test
    void 연결_테스트에서_API_Key를_비워두면_저장된_키를_사용한다() {
        llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, "100.100.0.10", 1234, "model", "secret", false));

        llmSettingService.testConnection(userId, new LlmConnectionTestRequest(LlmProvider.LMSTUDIO, "100.100.0.10", 1234, ""));

        assertThat(fakeLlmClientFactory.lastConnection().apiKey()).isEqualTo("secret");
    }

    // ===================== 다른 회원이 등록한 LLM 서버 =====================

    private static final String SHARED_HOST = "100.100.0.50";
    private static final String OWNER_TOKEN = "owner-token";

    /** 다른 회원이 먼저 등록한 LLM 서버 */
    private void registerByOther() {
        Long otherId = testUsers.create("owner").getUserId();
        llmSettingService.save(otherId, request(LlmProvider.LMSTUDIO, SHARED_HOST, 1234, "model", OWNER_TOKEN, false));
    }

    /** 인증을 켠 LLM 서버 — 토큰이 없거나 틀리면 401 */
    private void serverRequiresToken() {
        fakeLlmClient.willListModels(() -> {
            if (OWNER_TOKEN.equals(fakeLlmClientFactory.lastConnection().apiKey())) {
                return Optional.of(List.of("model"));
            }
            throw new LlmAuthException("LLM 서버 인증에 실패했어요. API Key를 확인해주세요.");
        });
    }

    @Test
    void 다른_회원이_등록한_서버는_API_Key_없이_저장할_수_없다() {
        registerByOther();

        assertThatThrownBy(() -> llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, SHARED_HOST, 1234, "model", null, false)))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage(LlmSettingService.SHARED_KEY_REQUIRED_MESSAGE)
                .extracting("field").isEqualTo("apiKey");
    }

    @Test
    void 다른_회원이_등록한_서버는_그_서버의_토큰이_맞아야_저장된다() {
        registerByOther();
        serverRequiresToken();

        assertThatThrownBy(() -> llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, SHARED_HOST, 1234, "model", "guess", false)))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage(LlmSettingService.SHARED_WRONG_KEY_MESSAGE);

        LlmSettingResponse saved = llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, SHARED_HOST, 1234, "model", OWNER_TOKEN, false));
        assertThat(saved.host()).isEqualTo(SHARED_HOST);
    }

    @Test
    void 인증이_꺼진_서버는_아무_토큰이나_통과하므로_다른_회원이_등록할_수_없다() {
        registerByOther();
        fakeLlmClient.willListModels(() -> Optional.of(List.of("model")));

        assertThatThrownBy(() -> llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, SHARED_HOST, 1234, "model", "anything", false)))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage(LlmSettingService.SHARED_NO_AUTH_MESSAGE);
    }

    @Test
    void 다른_회원이_등록한_서버에_연결할_수_없으면_토큰을_확인할_수_없어_거부한다() {
        registerByOther();
        fakeLlmClient.willListModels(() -> {
            throw new LlmException("연결할 수 없어요.");
        });

        assertThatThrownBy(() -> llmSettingService.save(userId, request(LlmProvider.LMSTUDIO, SHARED_HOST, 1234, "model", OWNER_TOKEN, false)))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage(LlmSettingService.SHARED_UNREACHABLE_MESSAGE)
                .extracting("field").isEqualTo("host");
    }

    @Test
    void IPv6는_표기가_달라도_같은_서버로_본다() {
        Long otherId = testUsers.create("owner").getUserId();
        llmSettingService.save(otherId, request(LlmProvider.LMSTUDIO, "fd7a:115c:a1e0::5", 1234, "model", OWNER_TOKEN, false));

        assertThatThrownBy(() -> llmSettingService.save(userId,
                request(LlmProvider.LMSTUDIO, "fd7a:115c:a1e0:0:0:0:0:5", 1234, "model", null, false)))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void 포트가_다르거나_이미_내가_등록한_주소면_토큰을_확인하지_않는다() {
        registerByOther();
        fakeLlmClient.willListModels(() -> {
            throw new AssertionError("토큰 확인 요청을 보내면 안 됨");
        });

        llmSettingService.save(userId, request(LlmProvider.OLLAMA, SHARED_HOST, 11434, "model", null, false));
        llmSettingService.save(userId, request(LlmProvider.OLLAMA, SHARED_HOST, 11434, "other-model", null, false));
    }

    private static LlmSettingRequest request(LlmProvider provider, String host, int port, String model,
                                             String apiKey, boolean clearApiKey) {
        return new LlmSettingRequest(provider, host, port, model, apiKey, clearApiKey);
    }
}
