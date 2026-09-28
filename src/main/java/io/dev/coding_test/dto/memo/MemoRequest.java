package io.dev.coding_test.dto.memo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 메모 작성 요청.
 * <p>
 * REST API 요청 본문({@code @RequestBody})과 화면 폼({@code @ModelAttribute}) 바인딩에 함께 사용한다.
 * Thymeleaf {@code th:field} 바인딩을 위해 record 대신 getter/setter 클래스로 둔다.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemoRequest {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int CONTENT_MAX_LENGTH = 20_000;

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = TITLE_MAX_LENGTH, message = "제목은 200자 이하로 입력해주세요.")
    private String title;

    @NotBlank(message = "본문을 입력해주세요.")
    @Size(max = CONTENT_MAX_LENGTH, message = "본문은 20,000자 이하로 입력해주세요.")
    private String content;
}
