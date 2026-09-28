package io.dev.coding_test.common.util;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 화면 페이지네이션에 표시할 페이지 번호 범위를 계산하는 유틸리티.
 */
public final class PageRangeUtil {

    private PageRangeUtil() {
    }

    /**
     * 현재 페이지를 가운데에 두고 최대 {@code window}개의 페이지 번호(0부터 시작)를 반환한다.
     *
     * @param current    현재 페이지 번호 (0부터 시작)
     * @param totalPages 전체 페이지 수
     * @param window     표시할 최대 페이지 수
     * @return 표시할 페이지 번호 목록, 페이지가 없으면 빈 리스트
     */
    public static List<Integer> pageNumbers(int current, int totalPages, int window) {
        if (totalPages <= 0) {
            return List.of();
        }
        int start = Math.max(0, current - window / 2);
        int end = Math.min(totalPages, start + window);
        start = Math.max(0, end - window);
        return IntStream.range(start, end).boxed().toList();
    }
}
