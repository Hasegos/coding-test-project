package io.dev.coding_test.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageRangeUtilTest {

    @Test
    void 현재_페이지를_가운데에_둔다() {
        assertThat(PageRangeUtil.pageNumbers(5, 20, 5)).containsExactly(3, 4, 5, 6, 7);
    }

    @Test
    void 처음과_끝에서는_범위를_안쪽으로_당긴다() {
        assertThat(PageRangeUtil.pageNumbers(0, 20, 5)).containsExactly(0, 1, 2, 3, 4);
        assertThat(PageRangeUtil.pageNumbers(19, 20, 5)).containsExactly(15, 16, 17, 18, 19);
    }

    @Test
    void 전체_페이지가_적으면_전체를_반환한다() {
        assertThat(PageRangeUtil.pageNumbers(1, 3, 5)).containsExactly(0, 1, 2);
        assertThat(PageRangeUtil.pageNumbers(0, 0, 5)).isEmpty();
    }
}
