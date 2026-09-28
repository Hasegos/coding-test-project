-- =====================================================================
-- AI Memo PostgreSQL 스키마
-- 운영(prod) 프로파일은 ddl-auto: validate 이므로 최초 배포 전에 이 스크립트로 테이블을 생성한다.
-- psql -U $POSTGRESQL_USERNAME -d $POSTGRESQL_DATABASE -f schema.sql
-- =====================================================================

-- 메모
CREATE TABLE IF NOT EXISTS memo (
    memo_id         BIGSERIAL       PRIMARY KEY,
    title           VARCHAR(200)    NOT NULL,
    content         TEXT            NOT NULL,
    revision        BIGINT          NOT NULL DEFAULT 0,          -- 제목/본문 수정 시 증가 (오래된 요약 결과 폐기 기준)
    summary_status  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | DONE | FAILED
    summary         TEXT,
    summary_error   VARCHAR(500),
    summary_model   VARCHAR(100),
    summarized_at   TIMESTAMP(6),
    created_at      TIMESTAMP(6)    NOT NULL,
    updated_at      TIMESTAMP(6)    NOT NULL,
    CONSTRAINT ck_memo_summary_status CHECK (summary_status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED'))
);

-- 목록 최신순 정렬
CREATE INDEX IF NOT EXISTS idx_memo_created_at ON memo (created_at DESC, memo_id DESC);

-- 기동 시 미완료 요약(PENDING/PROCESSING) 재요청 조회
CREATE INDEX IF NOT EXISTS idx_memo_summary_status ON memo (summary_status);

-- LLM이 추출한 할 일
CREATE TABLE IF NOT EXISTS memo_todo (
    todo_id     BIGSERIAL       PRIMARY KEY,
    memo_id     BIGINT          NOT NULL REFERENCES memo (memo_id) ON DELETE CASCADE,
    content     VARCHAR(500)    NOT NULL,
    sort_order  INTEGER         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_memo_todo_memo_id ON memo_todo (memo_id, sort_order);

-- 로컬 LLM 서버 접속 설정 (LLM 설정 화면에서 저장, 단일 행)
CREATE TABLE IF NOT EXISTS llm_setting (
    setting_id  BIGINT          PRIMARY KEY,                    -- 항상 1
    provider    VARCHAR(20)     NOT NULL,                       -- OLLAMA | LMSTUDIO
    host        VARCHAR(15)     NOT NULL,                       -- 로컬 전용 IPv4 (사설망 · Tailscale 대역)
    port        INTEGER         NOT NULL,
    model       VARCHAR(100)    NOT NULL,
    api_key     VARCHAR(200),
    updated_at  TIMESTAMP(6)    NOT NULL,
    CONSTRAINT ck_llm_setting_provider CHECK (provider IN ('OLLAMA', 'LMSTUDIO')),
    CONSTRAINT ck_llm_setting_port CHECK (port BETWEEN 1 AND 65535)
);

-- (선택) 메모가 많아져 제목·본문 검색(LIKE '%키워드%')이 느려지면 trigram 인덱스를 추가한다.
-- pg_trgm 확장 설치 권한이 필요하다.
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- CREATE INDEX IF NOT EXISTS idx_memo_title_trgm   ON memo USING gin (lower(title) gin_trgm_ops);
-- CREATE INDEX IF NOT EXISTS idx_memo_content_trgm ON memo USING gin (lower(content) gin_trgm_ops);
