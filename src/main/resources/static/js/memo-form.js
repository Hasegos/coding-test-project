/**
 * 메모 작성 폼 — 글자 수 표시 / Ctrl+Enter 저장 / 중복 제출 방지
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const form = document.querySelector('[data-memo-form]');
        if (!form) return;

        /* ===================== 글자 수 표시 ===================== */
        form.querySelectorAll('[data-counter-for]').forEach(function (counter) {
            const input = document.getElementById(counter.dataset.counterFor);
            if (!input) return;
            const max = Number(input.getAttribute('maxlength')) || 0;

            function render() {
                const length = input.value.length;
                counter.textContent = length.toLocaleString() + ' / ' + max.toLocaleString();
                counter.classList.toggle('field__counter--over', max > 0 && length >= max);
            }

            input.addEventListener('input', render);
            render();
        });

        /* ===================== Ctrl(⌘)+Enter 저장 ===================== */
        form.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                e.preventDefault();
                form.requestSubmit();
            }
        });

        /* ===================== 중복 제출 방지 ===================== */
        form.addEventListener('submit', function () {
            const submit = form.querySelector('[data-submit]');
            if (submit) {
                submit.disabled = true;
                submit.textContent = '저장 중…';
            }
        });
    });
})();
