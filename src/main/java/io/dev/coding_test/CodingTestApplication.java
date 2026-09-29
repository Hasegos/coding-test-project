package io.dev.coding_test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 로그인 기능이 없으므로 기본 사용자(임시 비밀번호) 생성을 끈다. Spring Security는 CSRF·보안 헤더 용도로만 사용한다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CodingTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodingTestApplication.class, args);
	}

}
