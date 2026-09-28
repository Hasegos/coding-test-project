package io.dev.coding_test.controller;

import io.dev.coding_test.dto.MemoRequest;
import io.dev.coding_test.dto.MemoResponse;
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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemoApiControllerTest {

    @Autowired
    private TestMembers testMembers;

    @Autowired
    private TestLoginContext testLoginContext;

    /** 로그인한 회원 (테스트마다 새로 가입) */
    private Long memberId;

    @BeforeEach
    void loginMember() {
        memberId = testMembers.login("tester").getMemberId();
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
    void 메모_작성_성공시_201과_저장된_메모를_반환한다() throws Exception {
        mockMvc.perform(post("/api/memos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "주간 회의", "content": "배포 일정 논의"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/memos/")))
                .andExpect(jsonPath("$.memoId").isNumber())
                .andExpect(jsonPath("$.title").value("주간 회의"))
                .andExpect(jsonPath("$.content").value("배포 일정 논의"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void 제목이_비어있으면_400과_필드_에러를_반환한다() throws Exception {
        mockMvc.perform(post("/api/memos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": " ", "content": "본문"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void JSON_본문이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/memos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void 메모_목록을_페이지_형식으로_반환한다() throws Exception {
        memoService.create(memberId, new MemoRequest("주간 회의", "배포 일정 논의"));
        memoService.create(memberId, new MemoRequest("장보기", "우유"));

        mockMvc.perform(get("/api/memos").param("keyword", "회의"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0].title").value("주간 회의"))
                .andExpect(jsonPath("$.content[0].preview").value("배포 일정 논의"));
    }

    @Test
    void 메모_단건을_조회한다() throws Exception {
        MemoResponse memo = memoService.create(memberId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(get("/api/memos/{id}", memo.memoId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memoId").value(memo.memoId()))
                .andExpect(jsonPath("$.content").value("배포 일정 논의"));
    }

    @Test
    void 존재하지_않는_메모를_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/memos/{id}", 9_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void 메모를_수정한다() throws Exception {
        MemoResponse memo = memoService.create(memberId, new MemoRequest("초안", "초안 본문"));

        mockMvc.perform(put("/api/memos/{id}", memo.memoId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "최종", "content": "최종 본문"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("최종"))
                .andExpect(jsonPath("$.content").value("최종 본문"));
    }

    @Test
    void 수정_요청이_잘못되면_400을_반환한다() throws Exception {
        MemoResponse memo = memoService.create(memberId, new MemoRequest("초안", "초안 본문"));

        mockMvc.perform(put("/api/memos/{id}", memo.memoId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "최종", "content": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    @Test
    void 메모를_삭제하면_204를_반환하고_이후_조회는_404다() throws Exception {
        MemoResponse memo = memoService.create(memberId, new MemoRequest("삭제할 메모", "본문"));

        mockMvc.perform(delete("/api/memos/{id}", memo.memoId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/memos/{id}", memo.memoId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 존재하지_않는_메모를_수정_삭제하면_404를_반환한다() throws Exception {
        mockMvc.perform(put("/api/memos/{id}", 9_999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "제목", "content": "본문"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/memos/{id}", 9_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void 메모_조회시_요약_상태를_함께_반환한다() throws Exception {
        MemoResponse memo = memoService.create(memberId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(get("/api/memos/{id}", memo.memoId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("PENDING"))
                .andExpect(jsonPath("$.summary.todos").isArray());
        mockMvc.perform(get("/api/memos").param("keyword", "회의"))
                .andExpect(jsonPath("$.content[0].summaryStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].todoCount").value(0));
    }

    @Test
    void 요약_결과를_조회한다() throws Exception {
        MemoResponse memo = memoService.create(memberId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(get("/api/memos/{id}/summary", memo.memoId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.summary").doesNotExist());
    }

    @Test
    void 재요약_요청은_202를_반환한다() throws Exception {
        MemoResponse memo = memoService.create(memberId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(post("/api/memos/{id}/summary", memo.memoId()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 존재하지_않는_메모의_요약_조회_재요약은_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/memos/{id}/summary", 9_999))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/memos/{id}/summary", 9_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void 요약_상태만_조회한다() throws Exception {
        MemoResponse memo = memoService.create(memberId, new MemoRequest("주간 회의", "배포 일정 논의"));

        mockMvc.perform(get("/api/memos/{id}/summary/status", memo.memoId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.inProgress").value(true))
                .andExpect(jsonPath("$.summary").doesNotExist());
        mockMvc.perform(get("/api/memos/{id}/summary/status", 9_999))
                .andExpect(status().isNotFound());
    }
}
