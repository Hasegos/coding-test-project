package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.DuplicateUsernameException;
import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.dto.SignupRequest;
import io.dev.coding_test.model.Member;
import io.dev.coding_test.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

/**
 * 회원가입을 처리하는 서비스.
 * <p>
 * 비밀번호는 {@link PasswordEncoder}(BCrypt)로 해시해서 저장한다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    public static final String PASSWORD_MISMATCH_MESSAGE = "비밀번호가 일치하지 않아요.";
    public static final String DUPLICATE_USERNAME_MESSAGE = "이미 사용 중인 아이디예요.";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 형식 검증을 통과한 가입 요청에서 비밀번호 확인 일치와 아이디 중복을 검사한다.
     *
     * @param request 회원가입 요청
     * @param errors  검사 결과를 담을 필드 오류
     */
    @Transactional(readOnly = true)
    public void validate(SignupRequest request, Errors errors) {
        if (!errors.hasFieldErrors("password") && !errors.hasFieldErrors("passwordConfirm")
                && !request.getPassword().equals(request.getPasswordConfirm())) {
            errors.rejectValue("passwordConfirm", "mismatch", PASSWORD_MISMATCH_MESSAGE);
        }
        if (!errors.hasFieldErrors("username") && memberRepository.existsByUsername(request.getUsername())) {
            errors.rejectValue("username", "duplicate", DUPLICATE_USERNAME_MESSAGE);
        }
    }

    /**
     * 회원을 가입시킨다. ({@link #validate} 통과 후 호출)
     *
     * @param request 회원가입 요청
     * @return 가입한 회원 ID
     * @throws DuplicateUsernameException 동시에 같은 아이디로 가입해 저장에 실패한 경우
     */
    @Transactional
    public Long signup(SignupRequest request) {
        Member member = new Member();
        member.setUsername(request.getUsername());
        member.setPassword(passwordEncoder.encode(request.getPassword()));
        member.setNickname(request.getNickname());
        member.setCreatedAt(TimeUtil.now());
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException(DUPLICATE_USERNAME_MESSAGE);
        }
        log.info("회원가입 - memberId: {}, username: {}", member.getMemberId(), member.getUsername());
        return member.getMemberId();
    }
}
