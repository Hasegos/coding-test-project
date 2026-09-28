/**
 * 공통 JS — 공용 헬퍼(JSON 요청·토스트) / 테마 전환
 */
(function () {
    'use strict';

    window.AiMemo = window.AiMemo || {};

    const THEME_KEY = 'ai-memo-theme';

    /* ===================== 공용 헬퍼 ===================== */

    /**
     * 페이지에 심어둔 CSRF 토큰 헤더를 반환한다. (Spring Security)
     */
    function csrfHeaders() {
        const token = document.querySelector('meta[name="_csrf"]');
        const header = document.querySelector('meta[name="_csrf_header"]');
        if (!token || !header) return {};
        const headers = {};
        headers[header.content] = token.content;
        return headers;
    }

    /**
     * JSON API 요청 헬퍼. 변경 요청에는 CSRF 토큰을 붙이고, 응답이 2xx가 아니면 서버 에러 메시지로 reject 한다.
     */
    function requestJson(url, options) {
        const opts = Object.assign({}, options || {});
        const method = (opts.method || 'GET').toUpperCase();
        opts.headers = Object.assign({ 'Accept': 'application/json' },
            method === 'GET' ? {} : csrfHeaders(), opts.headers || {});
        if (opts.body !== undefined && typeof opts.body !== 'string') {
            opts.headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(opts.body);
        }
        return fetch(url, opts).then(function (res) {
            if (res.status === 204) return null;
            return res.json().catch(function () { return null; }).then(function (data) {
                if (!res.ok) {
                    // 필드 검증 실패(400)면 첫 번째 필드 메시지를 우선 보여준다.
                    const fieldMessage = data && data.errors && data.errors.length ? data.errors[0].message : null;
                    const error = new Error(fieldMessage || (data && data.message) || 'HTTP ' + res.status);
                    error.status = res.status;
                    error.data = data;
                    throw error;
                }
                return data;
            });
        });
    }

    function showToast(message) {
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.setAttribute('role', 'status');
        toast.textContent = message;
        document.body.appendChild(toast);

        requestAnimationFrame(function () {
            toast.classList.add('toast--visible');
        });

        setTimeout(function () {
            toast.classList.remove('toast--visible');
            setTimeout(function () { toast.remove(); }, 250);
        }, 2200);
    }

    window.AiMemo.requestJson = requestJson;
    window.AiMemo.showToast = showToast;

    /* ===================== 테마 ===================== */

    function currentTheme() {
        const attr = document.documentElement.getAttribute('data-theme');
        if (attr) return attr;
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    document.addEventListener('DOMContentLoaded', function () {
        /* 리다이렉트 후 flash 메시지 토스트 */
        const flash = document.querySelector('[data-flash-toast]');
        if (flash && flash.dataset.message) {
            showToast(flash.dataset.message);
        }

        /* 확인이 필요한 폼(삭제 등) */
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                if (!window.confirm(form.dataset.confirm)) {
                    e.preventDefault();
                }
            });
        });

        document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const next = currentTheme() === 'dark' ? 'light' : 'dark';
                document.documentElement.setAttribute('data-theme', next);
                try { localStorage.setItem(THEME_KEY, next); } catch (e) { }
            });
        });
    });
})();
