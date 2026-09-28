/**
 * LLM 설정 — 런타임별 기본 포트 / 주소 붙여넣기 정리 / 연결 테스트(모델 목록 불러오기) / 모델 선택
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const form = document.querySelector('[data-llm-form]');
        if (!form) return;

        const hostInput = form.querySelector('#host');
        const portInput = form.querySelector('#port');
        const apiKeyInput = form.querySelector('#apiKey');
        const modelInput = form.querySelector('#model');
        const datalist = form.querySelector('#model-options');
        const chips = form.querySelector('[data-model-chips]');
        const result = form.querySelector('[data-test-result]');
        const testButton = form.querySelector('[data-test-connection]');

        /* ===================== 주소 붙여넣기 정리 ===================== */
        // LM Studio의 "Reachable at" 값(http://100.66.180.73:1234)처럼 주소 전체를 붙여넣으면 IP와 포트로 나눈다.
        function normalizeHost() {
            const value = hostInput.value.trim();
            let host = value;
            let port = '';

            if (/^[a-z][a-z0-9+.-]*:\/\//i.test(value)) {
                try {
                    const url = new URL(value);
                    host = url.hostname;
                    port = url.port;
                } catch (e) {
                    return;
                }
            } else {
                const ipv4WithPort = value.match(/^(\d{1,3}(?:\.\d{1,3}){3}):(\d{1,5})\/?$/);
                const ipv6WithPort = value.match(/^\[([0-9a-f:.]+)\](?::(\d{1,5}))?\/?$/i);
                if (ipv4WithPort) {
                    host = ipv4WithPort[1];
                    port = ipv4WithPort[2];
                } else if (ipv6WithPort) {
                    host = ipv6WithPort[1];
                    port = ipv6WithPort[2] || '';
                } else {
                    host = value.replace(/\/+$/, '');
                }
            }

            host = host.replace(/^\[|\]$/g, '');
            if (host !== hostInput.value) hostInput.value = host;
            if (port) portInput.value = port;
        }

        hostInput.addEventListener('paste', function () {
            setTimeout(normalizeHost, 0);
        });
        hostInput.addEventListener('change', normalizeHost);
        form.addEventListener('submit', normalizeHost);

        function selectedProvider() {
            return form.querySelector('input[name="provider"]:checked');
        }

        /* ===================== 런타임 변경 시 기본 포트 ===================== */
        let previousDefault = selectedProvider() ? selectedProvider().dataset.defaultPort : null;
        form.querySelectorAll('input[name="provider"]').forEach(function (radio) {
            radio.addEventListener('change', function () {
                // 포트를 직접 바꾸지 않았다면 새 런타임의 기본 포트로 맞춘다.
                if (!portInput.value || portInput.value === previousDefault) {
                    portInput.value = radio.dataset.defaultPort;
                }
                previousDefault = radio.dataset.defaultPort;
                clearModels();
            });
        });

        /* ===================== 모델 목록 표시 ===================== */
        function clearModels() {
            datalist.replaceChildren();
            chips.replaceChildren();
        }

        function renderModels(models) {
            clearModels();
            models.forEach(function (name) {
                const option = document.createElement('option');
                option.value = name;
                datalist.appendChild(option);

                const chip = document.createElement('button');
                chip.type = 'button';
                chip.className = 'model-chip';
                chip.textContent = name;
                chip.classList.toggle('model-chip--selected', modelInput.value === name);
                chip.addEventListener('click', function () {
                    modelInput.value = name;
                    chips.querySelectorAll('.model-chip').forEach(function (c) {
                        c.classList.toggle('model-chip--selected', c === chip);
                    });
                });
                chips.appendChild(chip);
            });
        }

        function showResult(message, type) {
            result.textContent = message;
            result.classList.toggle('connection-test__result--ok', type === 'ok');
            result.classList.toggle('connection-test__result--error', type === 'error');
        }

        /* ===================== 연결 테스트 ===================== */
        testButton.addEventListener('click', function () {
            normalizeHost();
            const provider = selectedProvider();
            testButton.disabled = true;
            showResult('연결하는 중…');

            window.AiMemo.requestJson('/api/settings/llm/test', {
                method: 'POST',
                body: {
                    provider: provider ? provider.value : null,
                    host: hostInput.value.trim(),
                    port: portInput.value ? Number(portInput.value) : null,
                    apiKey: apiKeyInput.value
                }
            }).then(function (response) {
                // 연결 실패도 200 + ok=false 로 온다. (입력값 검증 실패만 400)
                if (!response.ok) {
                    clearModels();
                    showResult(response.message || '연결에 실패했어요.', 'error');
                    return;
                }
                const models = response.models || [];
                renderModels(models);
                if (models.length === 0) {
                    // 모델 목록 미지원(models=null) / 로드된 모델 없음: 연결은 됐으니 안내만 한다.
                    showResult('연결 성공 (' + response.latencyMs + 'ms) · ' + response.message);
                    return;
                }
                if (!modelInput.value && models.length === 1) {
                    modelInput.value = models[0];
                    renderModels(models);
                }
                showResult('연결 성공 (' + response.latencyMs + 'ms) · 모델 ' + models.length + '개를 불러왔어요.', 'ok');
            }).catch(function (err) {
                clearModels();
                showResult(err.message || '연결에 실패했어요.', 'error');
            }).finally(function () {
                testButton.disabled = false;
            });
        });
    });
})();
