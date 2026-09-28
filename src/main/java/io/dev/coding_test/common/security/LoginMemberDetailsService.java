package io.dev.coding_test.common.security;

import io.dev.coding_test.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시 아이디로 회원을 조회해 {@link LoginMember}로 변환한다. (폼 로그인 인증)
 */
@Service
@RequiredArgsConstructor
public class LoginMemberDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return memberRepository.findByUsername(username == null ? "" : username.strip())
                .map(member -> new LoginMember(member.getMemberId(), member.getUsername(),
                        member.getNickname(), member.getPassword()))
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 아이디"));
    }
}
