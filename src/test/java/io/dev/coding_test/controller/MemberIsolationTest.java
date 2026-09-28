package io.dev.coding_test.controller;

import io.dev.coding_test.common.security.LoginMember;
import io.dev.coding_test.dto.LlmSettingRequest;
import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.model.enums.LlmProvider;
import io.dev.coding_test.repository.MemoRepository;
import io.dev.coding_test.service.LlmSettingService;
import io.dev.coding_test.service.MemoService;
import io.dev.coding_test.support.TestLoginContext;
import io.dev.coding_test.support.TestMembers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회원 간 데이터 분리를 검증한다.
 * 다른 회원의 메모·LLM 설정은 조회·수정·삭제할 수 없고, 존재 여부도 드러나지 않아야 한다(404).
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestMembers testMembers;

    @Autowired
    private TestLoginContext testLoginContext;

    @Autowired
    private MemoService memoService;

    @Autowired
    private MemoRepository memoRepository;

    @Autowired
    private LlmSettingService llmSettingService;

    private LoginMember owner;
    private LoginMember other;
    private Long ownerMemoId;

    @BeforeEach
    void setUp() {
        owner = testMembers.create("owner");
        other = testMembers.create("other");
        ownerMemoId = memoService.create(owner.getMemberId(), new MemoRequest("주인 메모", "비밀 본문")).memoId();
        testLoginContext.loginAs(other);
    }

    @AfterEach
    void resetLogin() {
        testLoginContext.reset();
    }

    @Test
    void 다른_회원의_메모는_API로_조회_수정_삭제_요약할_수_없다() throws Exception {
        String id = String.valueOf(ownerMemoId);

        mockMvc.perform(get("/api/memos/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/memos/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"탈취\", \"content\": \"변경\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/memos/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/memos/" + id + "/summary")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/memos/" + id + "/summary/status")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/memos/" + id + "/summary")).andExpect(status().isNotFound());

        assertThat(memoRepository.findById(ownerMemoId)).get()
                .satisfies(memo -> assertThat(memo.getTitle()).isEqualTo("주인 메모"));
    }

    @Test
    void 다른_회원의_메모는_화면에서도_볼_수_없다() throws Exception {
        String id = String.valueOf(ownerMemoId);

        mockMvc.perform(get("/memos/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/memos/" + id + "/edit")).andExpect(status().isNotFound());
        mockMvc.perform(get("/memos/" + id + "/summary")).andExpect(status().isNotFound());
        mockMvc.perform(post("/memos/" + id).param("title", "탈취").param("content", "변경"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/memos/" + id + "/delete")).andExpect(status().isNotFound());
        mockMvc.perform(post("/memos/" + id + "/summary")).andExpect(status().isNotFound());

        assertThat(memoRepository.findById(ownerMemoId)).isPresent();
    }

    @Test
    void 목록과_검색에는_내_메모만_나온다() throws Exception {
        memoService.create(other.getMemberId(), new MemoRequest("내 메모", "본문"));

        mockMvc.perform(get("/api/memos"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("내 메모"));
        mockMvc.perform(get("/api/memos").param("keyword", "비밀"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/memos"))
                .andExpect(content().string(containsString("내 메모")))
                .andExpect(content().string(not(containsString("주인 메모"))));
    }

    @Test
    void LLM_설정은_회원마다_따로_저장된다() throws Exception {
        llmSettingService.save(owner.getMemberId(), new LlmSettingRequest(LlmProvider.LMSTUDIO, "100.66.180.73", 1234,
                "qwen2.5-7b-instruct", "owner-secret", false));

        mockMvc.perform(get("/api/settings/llm")).andExpect(status().isNotFound());
        mockMvc.perform(get("/memos"))
                .andExpect(content().string(containsString("LLM 서버가 아직 연결되지 않았어요")));

        mockMvc.perform(put("/api/settings/llm").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "OLLAMA", "host": "192.168.0.20", "port": 11434, "model": "llama3.2:3b"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApiKey").value(false));

        assertThat(llmSettingService.getSetting(owner.getMemberId())).get()
                .satisfies(setting -> {
                    assertThat(setting.host()).isEqualTo("100.66.180.73");
                    assertThat(setting.hasApiKey()).isTrue();
                });
        assertThat(llmSettingService.getSetting(other.getMemberId())).get()
                .satisfies(setting -> assertThat(setting.host()).isEqualTo("192.168.0.20"));
    }
}
