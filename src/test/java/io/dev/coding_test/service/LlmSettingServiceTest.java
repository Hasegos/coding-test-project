package io.dev.coding_test.service;

import io.dev.coding_test.dto.LlmModelsRequest;
import io.dev.coding_test.dto.LlmSettingRequest;
import io.dev.coding_test.dto.LlmSettingResponse;
import io.dev.coding_test.llm.dto.LlmConnection;
import io.dev.coding_test.model.LlmSetting;
import io.dev.coding_test.model.enums.LlmProvider;
import io.dev.coding_test.repository.LlmSettingRepository;
import io.dev.coding_test.support.FakeLlmClient;
import io.dev.coding_test.support.FakeLlmClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class LlmSettingServiceTest {

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
        fakeLlmClient.reset();
    }

    @Test
    void 설정하지_않았으면_빈_값을_반환한다() {
        assertThat(llmSettingService.getSetting()).isEmpty();
        assertThat(llmSettingService.findConnection()).isEmpty();
        assertThat(llmSettingService.isConfigured()).isFalse();
    }

    @Test
    void 접속_설정을_단일_행으로_저장하고_다시_저장하면_덮어쓴다() {
        llmSettingService.save(request(LlmProvider.OLLAMA, " 192.168.0.10 ", 11434, " qwen2.5:7b ", null, false));
        LlmSettingResponse saved = llmSettingService.save(
                request(LlmProvider.LMSTUDIO, "100.66.180.73", 1234, "qwen2.5-vl-7b-instruct", null, false));

        assertThat(llmSettingRepository.count()).isEqualTo(1);
        assertThat(llmSettingRepository.findById(LlmSetting.SINGLETON_ID)).isPresent();
        assertThat(saved.provider()).isEqualTo(LlmProvider.LMSTUDIO);
        assertThat(saved.host()).isEqualTo("100.66.180.73");
        assertThat(saved.model()).isEqualTo("qwen2.5-vl-7b-instruct");
        assertThat(llmSettingService.isConfigured()).isTrue();
    }

    @Test
    void 저장_시_앞뒤_공백을_제거한다() {
        LlmSettingResponse saved = llmSettingService.save(
                request(LlmProvider.OLLAMA, " 192.168.0.10 ", 11434, " qwen2.5:7b ", null, false));

        assertThat(saved.host()).isEqualTo("192.168.0.10");
        assertThat(saved.model()).isEqualTo("qwen2.5:7b");
    }

    @Test
    void API_Key는_비워두면_유지하고_삭제를_선택하면_지운다() {
        llmSettingService.save(request(LlmProvider.LMSTUDIO, "127.0.0.1", 1234, "model", "secret", false));

        LlmSettingResponse kept = llmSettingService.save(request(LlmProvider.LMSTUDIO, "127.0.0.1", 1234, "model", "", false));
        assertThat(kept.hasApiKey()).isTrue();
        assertThat(llmSettingService.findConnection()).get().extracting(LlmConnection::apiKey).isEqualTo("secret");

        LlmSettingResponse cleared = llmSettingService.save(request(LlmProvider.LMSTUDIO, "127.0.0.1", 1234, "model", null, true));
        assertThat(cleared.hasApiKey()).isFalse();
    }

    @Test
    void 연결_테스트는_입력한_접속_정보로_모델_목록을_조회한다() {
        fakeLlmClient.willListModels(() -> List.of("qwen2.5-vl-7b-instruct"));

        List<String> models = llmSettingService.listModels(
                new LlmModelsRequest(LlmProvider.LMSTUDIO, "100.66.180.73", 1234, null));

        assertThat(models).containsExactly("qwen2.5-vl-7b-instruct");
        assertThat(fakeLlmClientFactory.lastConnection().baseUrl()).isEqualTo("http://100.66.180.73:1234");
    }

    @Test
    void 연결_테스트에서_API_Key를_비워두면_저장된_키를_사용한다() {
        llmSettingService.save(request(LlmProvider.LMSTUDIO, "127.0.0.1", 1234, "model", "secret", false));

        llmSettingService.listModels(new LlmModelsRequest(LlmProvider.LMSTUDIO, "127.0.0.1", 1234, ""));

        assertThat(fakeLlmClientFactory.lastConnection().apiKey()).isEqualTo("secret");
    }

    private static LlmSettingRequest request(LlmProvider provider, String host, int port, String model,
                                             String apiKey, boolean clearApiKey) {
        return new LlmSettingRequest(provider, host, port, model, apiKey, clearApiKey);
    }
}
