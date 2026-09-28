package io.dev.coding_test.dto;

import java.util.List;

/**
 * REST API 공통 에러 응답.
 *
 * @param status  HTTP 상태 코드
 * @param message 사용자에게 보여줄 에러 메시지
 * @param errors  필드 검증 실패 목록, 없으면 빈 리스트
 */
public record ErrorResponse(int status, String message, List<FieldError> errors) {

    /**
     * 필드 단위 검증 실패 정보.
     *
     * @param field   검증에 실패한 필드명
     * @param message 검증 실패 사유
     */
    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, List.of());
    }
}
