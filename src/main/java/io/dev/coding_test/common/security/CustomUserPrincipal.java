package io.dev.coding_test.common.security;

import io.dev.coding_test.model.User;
import io.dev.coding_test.model.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 로그인한 회원 정보 (Spring Security principal, 세션에 저장).
 * <p>
 * 인증이 끝나면 Spring Security가 {@link #eraseCredentials()}를 호출해 비밀번호 해시를 세션에 남기지 않는다.
 * </p>
 */
@Getter
public class CustomUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 회원 ID */
    private final Long userId;
    /** 아이디 */
    private final String username;
    /** 닉네임 (헤더 표시) */
    private final String nickname;
    /** 역할 */
    private final UserRole role;
    /** 비밀번호 해시, 인증 후 {@code null} */
    private String password;

    public CustomUserPrincipal(Long userId, String username, String nickname, UserRole role, String password) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.password = password;
    }

    /**
     * 회원 엔티티로 principal을 만든다. 엔티티 대신 필요한 값만 복사해 세션에 저장한다.
     *
     * @param user 회원 엔티티
     * @return 로그인 회원 정보
     */
    public static CustomUserPrincipal from(User user) {
        return new CustomUserPrincipal(user.getUserId(), user.getUsername(), user.getNickname(),
                user.getRole(), user.getPassword());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CustomUserPrincipal other && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }

    @Override
    public String toString() {
        return "CustomUserPrincipal[userId=" + userId + ", username=" + username + "]";
    }
}
