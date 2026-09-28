package io.dev.coding_test.common.util;

import io.dev.coding_test.model.enums.SummaryStatus;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 요약 상태 판단 유틸리티.
 */
public final class SummaryStatusUtil {

    /** 아직 요약 결과를 기다리는 상태 (대기, 요약 중) */
    public static final Set<SummaryStatus> IN_PROGRESS =
            Collections.unmodifiableSet(EnumSet.of(SummaryStatus.PENDING, SummaryStatus.PROCESSING));

    private SummaryStatusUtil() {
    }

    /**
     * 아직 요약 결과를 기다리는 상태인지 확인한다.
     *
     * @param status 요약 상태
     * @return PENDING 또는 PROCESSING이면 {@code true}
     */
    public static boolean isInProgress(SummaryStatus status) {
        return IN_PROGRESS.contains(status);
    }
}
