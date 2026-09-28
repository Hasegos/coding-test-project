/**
 * LLM 설정 — 런타임별 기본 포트 / 연결 테스트(모델 목록 불러오기) / 모델 선택
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
            const provider = selectedProvider();
            testButton.disabled = true;
            showResult('연결하는 중…');

            window.AiMemo.requestJson('/api/settings/llm/models', {
                method: 'POST',
                body: {
                    provider: provider ? provider.value : null,
                    host: hostInput.value.trim(),
                    port: portInput.value ? Number(portInput.value) : null,
                    apiKey: apiKeyInput.value
                }
            }).then(function (response) {
                const models = response.models || [];
                renderModels(models);
                if (models.length === 0) {
                    showResult('연결은 됐지만 불러올 수 있는 모델이 없어요. LLM 서버에서 모델을 먼저 로드해주세요.', 'error');
                    return;
                }
                if (!modelInput.value && models.length === 1) {
                    modelInput.value = models[0];
                    renderModels(models);
                }
                showResult('연결 성공 · 모델 ' + models.length + '개를 불러왔어요.', 'ok');
            }).catch(function (err) {
                clearModels();
                showResult(err.message || '연결에 실패했어요.', 'error');
            }).finally(function () {
                testButton.disabled = false;
            });
        });
    });
})();
