/**
 * 공통 JS — 공용 헬퍼(JSON 요청·토스트) / 테마 전환
 */
(function () {
    'use strict';

    window.AiMemo = window.AiMemo || {};

    const THEME_KEY = 'ai-memo-theme';

    /* ===================== 공용 헬퍼 ===================== */

    /**
     * JSON API 요청 헬퍼. 응답이 2xx가 아니면 서버 에러 메시지로 reject 한다.
     */
    function requestJson(url, options) {
        const opts = Object.assign({ headers: { 'Accept': 'application/json' } }, options || {});
        if (opts.body !== undefined && typeof opts.body !== 'string') {
            opts.headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(opts.body);
        }
        return fetch(url, opts).then(function (res) {
            if (res.status === 204) return null;
            return res.json().catch(function () { return null; }).then(function (data) {
                if (!res.ok) {
                    const message = data && data.message ? data.message : 'HTTP ' + res.status;
                    throw new Error(message);
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
        document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const next = currentTheme() === 'dark' ? 'light' : 'dark';
                document.documentElement.setAttribute('data-theme', next);
                try { localStorage.setItem(THEME_KEY, next); } catch (e) { }
            });
        });
    });
})();
