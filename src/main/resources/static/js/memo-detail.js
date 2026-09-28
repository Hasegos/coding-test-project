/**
 * 메모 상세 — AI 요약 상태 폴링 / 패널 갱신 / 재요약 요청
 */
(function () {
    'use strict';

    const POLL_INTERVAL_MS = 2000;
    const POLL_TIMEOUT_MS = 10 * 60 * 1000;

    let pollTimer = null;
    let pollStartedAt = 0;

    function panel() {
        return document.querySelector('[data-summary-panel]');
    }

    function isInProgress(status) {
        return status === 'PENDING' || status === 'PROCESSING';
    }

    /**
     * 서버에서 렌더링한 요약 패널 fragment로 교체한다.
     */
    function refreshPanel(memoId) {
        return fetch('/memos/' + memoId + '/summary', { headers: { 'Accept': 'text/html' } })
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.text();
            })
            .then(function (html) {
                const current = panel();
                if (!current) return;
                const template = document.createElement('template');
                template.innerHTML = html.trim();
                const next = template.content.querySelector('[data-summary-panel]');
                if (next) current.replaceWith(next);
            });
    }

    /**
     * 요약이 끝날 때까지 상태 API를 주기적으로 확인하고, 상태가 바뀌면 패널을 갱신한다.
     */
    function poll(memoId, knownStatus) {
        clearTimeout(pollTimer);
        if (Date.now() - pollStartedAt > POLL_TIMEOUT_MS) return;

        pollTimer = setTimeout(function () {
            window.AiMemo.requestJson('/api/memos/' + memoId + '/summary')
                .then(function (summary) {
                    if (summary.status === knownStatus) {
                        poll(memoId, knownStatus);
                        return;
                    }
                    return refreshPanel(memoId).then(function () {
                        if (summary.status === 'DONE') window.AiMemo.showToast('AI 요약이 완료됐어요.');
                        if (summary.status === 'FAILED') window.AiMemo.showToast('요약에 실패했어요.');
                        if (isInProgress(summary.status)) poll(memoId, summary.status);
                    });
                })
                .catch(function () {
                    poll(memoId, knownStatus); // 일시적 네트워크 오류는 다음 주기에 재시도
                });
        }, POLL_INTERVAL_MS);
    }

    function startPolling(memoId, status) {
        pollStartedAt = Date.now();
        poll(memoId, status);
    }

    document.addEventListener('DOMContentLoaded', function () {
        const initial = panel();
        if (!initial) return;
        const memoId = initial.dataset.memoId;

        if (isInProgress(initial.dataset.status)) {
            startPolling(memoId, initial.dataset.status);
        }

        // 패널이 교체되어도 동작하도록 이벤트 위임으로 재요약 요청 처리
        document.addEventListener('submit', function (e) {
            const form = e.target.closest('[data-summary-retry]');
            if (!form) return;
            e.preventDefault();

            const button = form.querySelector('button');
            if (button) button.disabled = true;

            window.AiMemo.requestJson('/api/memos/' + memoId + '/summary', { method: 'POST' })
                .then(function (summary) {
                    window.AiMemo.showToast('요약을 다시 요청했어요.');
                    return refreshPanel(memoId).then(function () {
                        if (isInProgress(summary.status)) startPolling(memoId, summary.status);
                    });
                })
                .catch(function (err) {
                    window.AiMemo.showToast(err.message || '요청에 실패했어요.');
                    if (button) button.disabled = false;
                });
        });
    });
})();
