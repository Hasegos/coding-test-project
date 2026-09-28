package io.dev.coding_test.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemoPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
}
