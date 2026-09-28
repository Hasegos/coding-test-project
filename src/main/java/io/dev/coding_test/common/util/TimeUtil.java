package io.dev.coding_test.common.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 시각 관련 유틸리티.
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    /**
     * DB(TIMESTAMP(6)) 정밀도에 맞춰 마이크로초 단위로 자른 현재 시각을 반환한다.
     * 저장 직후 응답과 재조회 결과의 시각이 달라지지 않도록 한다.
     *
     * @return 마이크로초 단위 현재 시각
     */
    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
