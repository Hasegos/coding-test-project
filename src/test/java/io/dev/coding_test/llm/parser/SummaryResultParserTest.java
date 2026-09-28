package io.dev.coding_test.llm.parser;

import io.dev.coding_test.llm.dto.SummaryResult;
import io.dev.coding_test.llm.exception.LlmException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummaryResultParserTest {

    private final SummaryResultParser parser = new SummaryResultParser(JsonMapper.builder().build());

    @Test
    void 정상_JSON을_해석한다() {
        SummaryResult result = parser.parse("""
                {"summary": "배포 일정을 논의했다.", "todos": ["배포 스크립트 점검하기", "QA 일정 공유하기"]}
                """);

        assertThat(result.summary()).isEqualTo("배포 일정을 논의했다.");
        assertThat(result.todos()).containsExactly("배포 스크립트 점검하기", "QA 일정 공유하기");
    }

    @Test
    void think_블록과_코드블록_설명문장을_무시한다() {
        SummaryResult result = parser.parse("""
                <think>사용자가 요약을 원한다 {"summary": "잘못된 값"}</think>
                요약 결과입니다.
                ```json
                {"summary": "회의 요약", "todos": []}
                ```
                """);

        assertThat(result.summary()).isEqualTo("회의 요약");
        assertThat(result.todos()).isEmpty();
    }

    @Test
    void 할_일의_공백_중복_빈값을_정리하고_객체형_항목도_해석한다() {
        SummaryResult result = parser.parse("""
                {"summary": "요약", "todos": ["  보고서   작성하기 ", "보고서 작성하기", "", 3, {"task": "메일 보내기"}]}
                """);

        assertThat(result.todos()).containsExactly("보고서 작성하기", "메일 보내기");
    }

    @Test
    void 할_일_개수와_길이를_제한한다() {
        String todos = IntStream.range(0, 30).mapToObj(i -> "\"할 일 " + i + "\"").collect(Collectors.joining(","));
        SummaryResult many = parser.parse("{\"summary\": \"요약\", \"todos\": [" + todos + "]}");
        SummaryResult longTodo = parser.parse("{\"summary\": \"요약\", \"todos\": [\"" + "가".repeat(600) + "\"]}");

        assertThat(many.todos()).hasSize(SummaryResultParser.MAX_TODO_COUNT);
        assertThat(longTodo.todos().getFirst()).hasSize(SummaryResultParser.MAX_TODO_LENGTH).endsWith("…");
    }

    @Test
    void todos가_없으면_빈_목록으로_처리한다() {
        assertThat(parser.parse("{\"summary\": \"요약\"}").todos()).isEmpty();
    }

    @Test
    void 해석할_수_없는_응답은_LlmException이_발생한다() {
        assertThatThrownBy(() -> parser.parse("")).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> parser.parse("요약할 수 없습니다.")).isInstanceOf(LlmException.class)
                .hasMessageContaining("JSON을 찾지 못했어요");
        assertThatThrownBy(() -> parser.parse("{\"summary\": ")).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> parser.parse("{\"summary\": \"  \", \"todos\": []}"))
                .isInstanceOf(LlmException.class).hasMessageContaining("summary");
        assertThatThrownBy(() -> parser.parse("{\"summary\": {\"text\": \"객체\"}}"))
                .isInstanceOf(LlmException.class);
    }
}
