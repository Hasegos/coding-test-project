/**
 * 테마 깜빡임 방지 — CSS 로딩 전에 저장된 테마를 적용한다. (CSP로 인라인 스크립트를 막기 위해 파일로 분리)
 */
(function () {
    'use strict';
    try {
        const theme = localStorage.getItem('ai-memo-theme');
        if (theme === 'light' || theme === 'dark') {
            document.documentElement.setAttribute('data-theme', theme);
        }
    } catch (e) { }
})();
