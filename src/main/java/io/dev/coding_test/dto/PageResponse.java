package io.dev.coding_test.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * REST API 페이지 응답.
 * <p>
 * Spring Data {@link Page}를 그대로 직렬화하면 내부 구조가 노출되고 버전마다 형식이 바뀔 수 있어
 * 필요한 필드만 담아 응답한다.
 * </p>
 *
 * @param content       현재 페이지 항목
 * @param page          현재 페이지 번호 (0부터 시작)
 * @param size          페이지 크기
 * @param totalElements 전체 항목 수
 * @param totalPages    전체 페이지 수
 * @param <T>           항목 타입
 */
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
