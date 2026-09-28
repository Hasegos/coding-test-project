package io.dev.coding_test.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원 역할. Spring Security 권한({@code ROLE_*})으로 변환해 사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum UserRole {

    /** 일반 회원 */
    USER("ROLE_USER");

    /** Spring Security 권한 이름 */
    private final String authority;
}
