package io.dev.coding_test.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemoApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
}
