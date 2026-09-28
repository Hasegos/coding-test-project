package io.dev.coding_test.controller;

import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
import io.dev.coding_test.service.MemoService;
import io.dev.coding_test.support.TestLoginContext;
import io.dev.coding_test.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemoPageControllerTest {

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private TestLoginContext testLoginContext;

    /** 로그인한 회원 (테스트마다 새로 가입) */
    private Long userId;

    @BeforeEach
    void loginUser() {
        userId = testUsers.login("tester").getUserId();
    }

    @AfterEach
    void resetLogin() {
        testLoginContext.reset();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemoService memoService;

    @Test
    void 작성_페이지를_렌더링한다() throws Exception {
        mockMvc.perform(get("/memos/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("memo/form"))
                .andExpect(content().string(containsString("새 메모")));
    }

    @Test
    void 작성_성공시_상세_페이지로_리다이렉트한다() throws Exception {
        mockMvc.perform(post("/memos")
                        .param("title", "주간 회의")
                        .param("content", "배포 일정 논의"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/memos/*"))
                .andExpect(flash().attribute("toast", "메모를 저장했어요."));
    }

    @Test
    void 검증_실패시_입력값을_유지한_채_폼을_다시_보여준다() throws Exception {
        mockMvc.perform(post("/memos")
                        .param("title", "")
                        .param("content", "유지될 본문"))
                .andExpect(status().isOk())
                .andExpect(view().name("memo/form"))
                .andExpect(model().attributeHasFieldErrors("memoRequest", "title"))
                .andExpect(content().string(containsString("유지될 본문")))
                .andExpect(content().string(containsString("제목을 입력해주세요.")));
    }

    @Test
    void 루트_요청은_메모_목록으로_리다이렉트한다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/memos"));
    }

    @Test
    void 목록_페이지에_메모와_검색어를_렌더링한다() throws Exception {
        memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(get("/memos").param("keyword", "회의"))
                .andExpect(status().isOk())
                .andExpect(view().name("memo/list"))
                .andExpect(model().attribute("keyword", "회의"))
                .andExpect(content().string(containsString("주간 회의")));
    }

    @Test
    void 메모가_없으면_빈_상태를_보여준다() throws Exception {
        mockMvc.perform(get("/memos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 작성한 메모가 없어요")));
    }

    @Test
    void 상세_페이지에_원문을_렌더링한다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "<script>alert(1)</script>"));

        mockMvc.perform(get("/memos/{id}", memo.memoId()))
                .andExpect(status().isOk())
                .andExpect(view().name("memo/detail"))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }

    @Test
    void 존재하지_않는_메모_상세는_404_에러_페이지를_보여준다() throws Exception {
        mockMvc.perform(get("/memos/{id}", 9_999))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void 메모_ID가_숫자가_아니면_400_에러_페이지를_보여준다() throws Exception {
        mockMvc.perform(get("/memos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/error"));
    }

    @Test
    void 수정_페이지에_기존_내용을_채워서_보여준다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("초안", "초안 본문"));

        mockMvc.perform(get("/memos/{id}/edit", memo.memoId()))
                .andExpect(status().isOk())
                .andExpect(view().name("memo/form"))
                .andExpect(model().attribute("memoId", memo.memoId()))
                .andExpect(content().string(containsString("메모 수정")))
                .andExpect(content().string(containsString("초안 본문")))
                .andExpect(content().string(containsString("action=\"/memos/" + memo.memoId() + "\"")));
    }

    @Test
    void 수정_성공시_상세_페이지로_리다이렉트한다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("초안", "초안 본문"));

        mockMvc.perform(post("/memos/{id}", memo.memoId())
                        .param("title", "최종")
                        .param("content", "최종 본문"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/memos/" + memo.memoId()))
                .andExpect(flash().attribute("toast", "메모를 수정했어요."));
    }

    @Test
    void 수정_검증_실패시_수정_폼을_다시_보여준다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("초안", "초안 본문"));

        mockMvc.perform(post("/memos/{id}", memo.memoId())
                        .param("title", "최종")
                        .param("content", " "))
                .andExpect(status().isOk())
                .andExpect(view().name("memo/form"))
                .andExpect(model().attribute("memoId", memo.memoId()))
                .andExpect(model().attributeHasFieldErrors("memoRequest", "content"));
    }

    @Test
    void 삭제_성공시_목록으로_리다이렉트한다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("삭제할 메모", "본문"));

        mockMvc.perform(post("/memos/{id}/delete", memo.memoId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/memos"))
                .andExpect(flash().attribute("toast", "메모를 삭제했어요."));
    }

    @Test
    void 존재하지_않는_메모_수정_페이지는_404_에러_페이지를_보여준다() throws Exception {
        mockMvc.perform(get("/memos/{id}/edit", 9_999))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void 상세_페이지에_AI_요약_패널을_렌더링한다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(get("/memos/{id}", memo.memoId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-summary-panel")))
                .andExpect(content().string(containsString("AI 요약")))
                .andExpect(content().string(containsString("요약 대기")));
    }

    @Test
    void 요약_패널_fragment만_렌더링한다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(get("/memos/{id}/summary", memo.memoId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-summary-panel")))
                .andExpect(content().string(containsString("data-status=\"PENDING\"")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void 재요약_요청후_상세_페이지로_리다이렉트한다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(post("/memos/{id}/summary", memo.memoId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/memos/" + memo.memoId()))
                .andExpect(flash().attribute("toast", "요약을 다시 요청했어요."));
    }

    @Test
    void 목록_카드에_요약_상태를_표시한다() throws Exception {
        memoService.create(userId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(get("/memos"))
                .andExpect(content().string(containsString("badge--pending")));
    }

    @Test
    void 목록과_상세에서_제목의_HTML을_이스케이프한다() throws Exception {
        MemoResponse memo = memoService.create(userId, new MemoRequest("<img src=x onerror=alert(1)>", "본문"));

        mockMvc.perform(get("/memos"))
                .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")))
                .andExpect(content().string(not(containsString("<img src=x"))));
        mockMvc.perform(get("/memos/{id}", memo.memoId()))
                .andExpect(content().string(not(containsString("<img src=x"))));
    }
}
