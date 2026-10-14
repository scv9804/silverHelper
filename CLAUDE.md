# CLAUDE.md

실버헬퍼: 보호자(자녀)를 위한 부모님 복지 혜택 안내 서비스.
공공 복지 데이터를 매일 수집하고, AI(Claude/GPT)가 구조화·요약·교차검증한다.

## 구조
- `backend/` Spring Boot 4 (Java 21, Gradle), 패키지 루트 `com.silverhelper`
- `frontend/` React 19 + TypeScript + Vite + Tailwind
- `docker-compose.yml` 로컬 개발용 PostgreSQL(pgvector) + Redis
- `infra/` 운영 배포 설정
- `docs/adr/` 설계 결정 기록, `docs/devlog/` 개발일지

## 명령어
- 로컬 DB/Redis: `docker compose up -d`
- 백엔드 실행: `cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` (PowerShell: `$env:SPRING_PROFILES_ACTIVE = 'local'; ./gradlew bootRun`)
- 백엔드 테스트: `cd backend && ./gradlew test` (Testcontainers 사용, Docker 필요)
- 프론트 실행: `cd frontend && npm run dev` (http://localhost:5173, `/api`는 8080으로 프록시)
- 프론트 검사: `cd frontend && npm run lint && npm test && npm run build`

## 백엔드 규칙
- 패키지는 도메인 기준: `com.silverhelper.{benefit,user,pipeline,notification,common}`
  - 각 도메인 안에 `controller / service / repository / domain / dto`
- 엔티티를 API 응답으로 직접 노출하지 않는다. 요청·응답은 `record` DTO.
- 설정값과 비밀값은 환경변수로만 주입한다 (`.env.example` 참고). 키를 코드에 쓰지 않는다.
- 새 기능에는 테스트를 함께 작성한다. DB가 필요한 테스트는 `TestcontainersConfiguration` 사용.
- 외부 API(공공데이터, AI) 호출은 인터페이스 뒤에 두고 테스트에서는 대체 구현을 쓴다.

## AI 파이프라인 원칙
- 정답 공급원은 공식 데이터다. AI는 추출·요약·검증만 한다.
- AI 출력은 항상 JSON 스키마로 받고 검증한 뒤 저장한다.
- 원문 해시가 바뀐 항목만 AI로 처리한다 (비용 통제).
- Claude 결과와 GPT 결과가 다르거나 검증에 실패하면 검토 대기열로 보낸다.
- 사용자에게 보여주는 모든 혜택 정보에는 출처 URL과 기준일을 포함한다.

## 작업 방식
- **멀티 에이전트 운영 규칙은 `docs/workflow.md`를 따른다** (팀 구성, 담당 폴더, herdr 사용법, 사용량 기록).
- 이 저장소의 Claude Code 세션은 기본적으로 팀장 `doyun` 역할이다. worktree에서 실행된 경우 배정된 작업자 역할을 따른다.
- 작업 시작과 끝에 `node scripts/track.mjs start|end`로 사용량을 기록한다.
- 이슈 1개 = 브랜치 1개 = PR 1개. 브랜치명 `feat/…`, `fix/…`, `docs/…`
- 커밋 메시지는 한국어, `feat: …` / `fix: …` 형식
- 큰 설계 결정은 `docs/adr/NNNN-제목.md`로 남긴다.
