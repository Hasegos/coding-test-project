package io.dev.coding_test.repository;

import io.dev.coding_test.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 회원 저장소.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 아이디로 회원을 조회한다. (로그인)
     *
     * @param username 아이디
     * @return 회원, 없으면 {@code Optional.empty()}
     */
    Optional<User> findByUsername(String username);

    /**
     * 아이디 사용 여부를 확인한다. (회원가입 중복 검사)
     *
     * @param username 아이디
     * @return 이미 사용 중이면 {@code true}
     */
    boolean existsByUsername(String username);
}
