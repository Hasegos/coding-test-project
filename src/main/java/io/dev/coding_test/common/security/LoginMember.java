package io.dev.coding_test.common.security;

import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
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
public class LoginMember implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<GrantedAuthority> AUTHORITIES = AuthorityUtils.createAuthorityList("ROLE_USER");

    /** 회원 ID */
    private final Long memberId;
    /** 아이디 */
    private final String username;
    /** 닉네임 (헤더 표시) */
    private final String nickname;
    /** 비밀번호 해시, 인증 후 {@code null} */
    private String password;

    public LoginMember(Long memberId, String username, String nickname, String password) {
        this.memberId = memberId;
        this.username = username;
        this.nickname = nickname;
        this.password = password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LoginMember other && Objects.equals(memberId, other.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(memberId);
    }

    @Override
    public String toString() {
        return "LoginMember[memberId=" + memberId + ", username=" + username + "]";
    }
}
