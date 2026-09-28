package io.dev.coding_test.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원 엔티티.
 * <p>
 * 비밀번호는 BCrypt 해시({@code {bcrypt}...})로만 저장한다. 값 변경 규칙은 {@code MemberService}가 담당한다.
 * </p>
 */
@Entity
@Getter
@Setter
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "uk_member_username", columnNames = "username"))
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "username", nullable = false, length = 20)
    private String username;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
