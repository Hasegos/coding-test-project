# AI Memo — AI 메모 자동 정리 프로그램

메모를 저장하면 로컬 LLM이 자동으로 **요약**하고 **할 일 목록**을 추출해주는 백엔드 시스템입니다.

## 주요 기능

- 메모 작성 및 저장 (제목, 본문, 작성일)
- 메모 저장 시 로컬 LLM 자동 호출을 통한 요약 및 할 일 추출
- 저장된 메모와 요약 결과 조회
- 메모 수정 및 삭제

## 기술 스택

| 구분 | 스택 |
|---|---|
| Backend | Java 21, Spring Boot |
| Database | PostgreSQL |
| AI 연동 | 로컬 LLM (Ollama / LM Studio) |
| 인프라 | Tailscale 기반 자체 서버 호스팅 |

## 브랜치 전략

| 브랜치 | 용도 |
|---|---|
| `master` | 배포 브랜치 (dev → master PR만 허용) |
| `dev` | 통합 개발 브랜치 |
| `feature/coding-test-{기능}` | 기능 단위 개발 브랜치, 완료 후 dev로 PR |
