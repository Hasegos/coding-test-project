package io.dev.coding_test.service;

import io.dev.coding_test.common.exception.DuplicateUsernameException;
import io.dev.coding_test.common.util.TimeUtil;
import io.dev.coding_test.common.validation.AuthPattern;
import io.dev.coding_test.dto.auth.SignupRequest;
import io.dev.coding_test.model.User;
import io.dev.coding_test.repository.UserRepository;
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
public class UserService {

    public static final String PASSWORD_MISMATCH_MESSAGE = "비밀번호가 일치하지 않아요.";
    public static final String DUPLICATE_USERNAME_MESSAGE = "이미 가입된 이메일이에요.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 형식 검증을 통과한 가입 요청에서 비밀번호 확인 일치와 아이디 중복을 검사한다.
     * 아이디(이메일)는 대소문자를 구분하지 않으므로 소문자로 바꿔 중복을 확인한다.
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
        if (!errors.hasFieldErrors("username")
                && userRepository.existsByUsername(AuthPattern.normalizeUsername(request.getUsername()))) {
            errors.rejectValue("username", "duplicate", DUPLICATE_USERNAME_MESSAGE);
        }
    }

    /**
     * 회원을 가입시킨다. ({@link #validate} 통과 후 호출) 아이디는 소문자로 저장한다.
     *
     * @param request 회원가입 요청
     * @return 가입한 회원 ID
     * @throws DuplicateUsernameException 동시에 같은 아이디로 가입해 저장에 실패한 경우
     */
    @Transactional
    public Long signup(SignupRequest request) {
        User user = new User();
        user.setUsername(AuthPattern.normalizeUsername(request.getUsername()));
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setCreatedAt(TimeUtil.now());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException(DUPLICATE_USERNAME_MESSAGE);
        }
        log.info("회원가입 - userId: {}, username: {}", user.getUserId(), user.getUsername());
        return user.getUserId();
    }
}
