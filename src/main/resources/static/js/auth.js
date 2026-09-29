/**
 * 로그인 · 회원가입 — 제출 전 입력 형식 검증
 * (서버 검증과 같은 규칙: io.dev.coding_test.common.validation.AuthPattern)
 */
(function () {
    'use strict';

    const USERNAME_PATTERN = /^(?!.*\.\.)[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
    const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9])[\x21-\x7E]{8,64}$/;
    const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9_]{2,12}$/;

    const MESSAGES = {
        usernameRequired: '아이디(이메일)를 입력해주세요.',
        username: '아이디는 이메일 형식(예: user@example.com)으로 100자 이하로 입력해주세요.',
        passwordRequired: '비밀번호를 입력해주세요.',
        password: '비밀번호는 영문·숫자·특수문자를 모두 포함해 8~64자로 입력해주세요. (공백·한글 불가)',
        passwordConfirmRequired: '비밀번호를 한 번 더 입력해주세요.',
        passwordMismatch: '비밀번호가 일치하지 않아요.',
        nicknameRequired: '닉네임을 입력해주세요.',
        nickname: '닉네임은 한글·영문·숫자·밑줄(_) 2~12자로 입력해주세요.'
    };

    function checkUsername(value) {
        const v = value.trim();
        if (!v) return MESSAGES.usernameRequired;
        return v.length <= 100 && USERNAME_PATTERN.test(v) ? '' : MESSAGES.username;
    }

    function checkPassword(value) {
        if (!value) return MESSAGES.passwordRequired;
        return PASSWORD_PATTERN.test(value) ? '' : MESSAGES.password;
    }

    document.addEventListener('DOMContentLoaded', function () {
        const form = document.querySelector('[data-auth-form]');
        if (!form) return;
        const isSignup = form.dataset.authForm === 'signup';

        const rules = {
            username: checkUsername,
            password: checkPassword
        };
        if (isSignup) {
            rules.passwordConfirm = function (value) {
                if (!value) return MESSAGES.passwordConfirmRequired;
                return value === form.elements.password.value ? '' : MESSAGES.passwordMismatch;
            };
            rules.nickname = function (value) {
                const v = value.trim();
                if (!v) return MESSAGES.nicknameRequired;
                return NICKNAME_PATTERN.test(v) ? '' : MESSAGES.nickname;
            };
        }

        function show(name, message) {
            const input = form.elements[name];
            const error = form.querySelector('[data-error-for="' + name + '"]');
            input.classList.toggle('field__input--error', !!message);
            input.setAttribute('aria-invalid', message ? 'true' : 'false');
            if (error) error.textContent = message;
        }

        function validate(name) {
            const message = rules[name](form.elements[name].value);
            show(name, message);
            return !message;
        }

        Object.keys(rules).forEach(function (name) {
            const input = form.elements[name];
            // 입력을 마쳤을 때 검사하고, 오류가 표시된 상태에서는 고치는 즉시 다시 검사한다.
            input.addEventListener('blur', function () {
                if (input.value) validate(name);
            });
            input.addEventListener('input', function () {
                if (input.classList.contains('field__input--error')) validate(name);
                if (name === 'password' && isSignup && form.elements.passwordConfirm.value) validate('passwordConfirm');
            });
        });

        form.addEventListener('submit', function (e) {
            let firstInvalid = null;
            Object.keys(rules).forEach(function (name) {
                if (!validate(name) && !firstInvalid) firstInvalid = form.elements[name];
            });
            if (firstInvalid) {
                e.preventDefault();
                firstInvalid.focus();
            }
        });
    });
})();
