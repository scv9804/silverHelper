# 멀티 에이전트 작업 방식

herdr로 여러 AI 코딩 에이전트를 동시에 돌리고, 모듈 단위로 나눠 개발한 뒤 PR로 합친다.

## 팀 구성

| herdr 이름 | 이름 | 모델 | 역할 | 작업 위치 |
|---|---|---|---|---|
| `doyun` | 이도윤 팀장 | Claude Code | 기획, 명세, 이슈 분할, 작업자 지시, 리뷰, 머지, 공유 파일 관리 | 메인 저장소 (`main`) |
| `seojun` | 김서준 | Codex | 백엔드 API (혜택 조회, 조건 매칭, 사용자·프로필) | worktree |
| `haeun` | 최하은 | Codex | 프론트엔드 화면 | worktree |
| `jiho` | 박지호 | Claude Code | AI 파이프라인 (수집 배치, AI 추출, 교차검증) | worktree |

### 왜 이렇게 나눴나
- **한도 분산**: Claude Pro와 ChatGPT Pro는 사용 한도가 따로다. 코드를 대량으로 쓰는 일은 한도가 넉넉한 Codex에, 판단이 많이 필요한 설계·리뷰·AI 로직은 Claude에 맡긴다.
- **교차 리뷰**: 서로 다른 모델이 서로의 PR을 리뷰해서 한 모델의 맹점을 다른 모델이 잡는다. 서비스의 Claude↔GPT 교차검증과 같은 발상이다.
- **동시 실행 제한**: Claude는 최대 2개(doyun + 작업자 1), Codex는 최대 2개.

## 진행 단계

### Phase 0: 계약 확정 (doyun 혼자, 순서대로)
모듈끼리 맞물리는 부분을 먼저 확정해야 동시 작업이 충돌하지 않는다.

| # | 작업 | 결과물 |
|---|---|---|
| 0-1 | 공공데이터 복지서비스 API 조사 | `docs/specs/public-api.md` |
| 0-2 | DB 스키마 설계 + Flyway 도입 | `docs/adr/0002-db-schema.md`, `backend/src/main/resources/db/migration/V1__init.sql` |
| 0-3 | REST API 명세 | `docs/specs/openapi.yaml` |
| 0-4 | 패키지 뼈대, 공통 모듈 (예외 처리, 응답 형식) | `com.silverhelper.common` |
| 0-5 | Phase 1 이슈 작성 (담당자, 담당 폴더, 완료 조건) | GitHub Issues |

### Phase 1: 동시 개발
| 브랜치 | 담당 | 내용 |
|---|---|---|
| `feat/benefit-api` | seojun | 혜택 목록·상세 API, 조건 매칭 |
| `feat/web-benefits` | haeun | 프로필 입력, 혜택 목록·상세 화면 (openapi.yaml 기준 가짜 API) |
| `feat/pipeline` | jiho | 공공 API 수집 → 변경 감지 → Claude 추출 → GPT 검증 → 검토 대기열 |

### Phase 2
로그인(카카오·구글) → 관리자 검토 화면 → 알림 → 챗봇(RAG) → AWS 배포

## 담당 폴더 (충돌 방지)

| 담당 | 수정 가능 |
|---|---|
| seojun | `backend/src/**/benefit/**`, `backend/src/**/user/**` |
| jiho | `backend/src/**/pipeline/**` |
| haeun | `frontend/**` (단 `package.json` 의존성 추가는 PR 설명에 명시) |
| **doyun만** | `build.gradle`, `application.yml`, `db/migration/**`, `common/**`, `docs/specs/**`, `CLAUDE.md`, `AGENTS.md`, CI 설정 |

작업자가 공유 파일 변경이 필요하면 직접 고치지 말고 doyun에게 요청한다.
DB 마이그레이션 파일 번호는 doyun이 배정한다.

## 작업 흐름

```
doyun: 이슈 작성 → herdr로 worktree 생성 → 작업자 실행 → 지시 전달
작업자: 개발 → 테스트 통과 → PR 생성 (본문에 이슈 번호)
CI 자동 실행
doyun: 상대 모델에게 리뷰 요청 (Claude 작업 → Codex 리뷰, Codex 작업 → Claude 리뷰)
작업자: 리뷰 반영
doyun: 머지 → worktree 정리 → 사용량 기록
사용자: 중요한 PR은 머지 전 직접 확인
```

### herdr 명령 예시 (doyun이 실행)
```bash
# worktree 만들기 (경로 규칙: D:/workspace/silverHelper-wt/<브랜치 끝 이름>)
herdr worktree create --branch feat/benefit-api --base main --path D:/workspace/silverHelper-wt/benefit-api --no-focus

# 만들어진 pane에서 작업자 실행
herdr agent start seojun --kind codex --pane <pane_id>

# 지시 전달, 상태 확인
herdr agent prompt seojun "이슈 #3을 진행해줘. AGENTS.md를 먼저 읽어." --wait
herdr agent list
```

## 사용량과 시간 기록

모든 작업은 시작과 끝에 기록한다. 결과는 [`docs/metrics/ledger.md`](metrics/ledger.md)에 쌓인다.

**기록은 지시하는 쪽(팀장 또는 기획 세션)이 한다.** 작업자에게 맡기면 일을 한참 한 뒤에야 `start`를 실행해 시간과 토큰이 빠진다(Phase 1에서 실제로 발생). 지시를 보내기 직전에 `start`, 보고를 받은 직후에 `end`를 실행한다.

```bash
# 작업 시작 (작업자의 worktree 폴더에서 실행. 하위 폴더에서 실행해도 worktree 전체를 집계)
node scripts/track.mjs start P1-benefit-api seojun codex "혜택 조회 API"

# 작업 종료
node scripts/track.mjs end P1-benefit-api "PR #5"

# 진행 중인 작업, 현재 한도 확인
node scripts/track.mjs status
```

- **토큰**: 내 PC에 남는 에이전트 로그(`~/.claude/projects`, `~/.codex/sessions`)에서 `start`를 실행한 폴더의 세션만 골라 구간 합계를 낸다.
- **한도 %**: Claude는 상태줄 스크립트(`scripts/statusline.mjs`)가, Codex는 세션 로그가 자동으로 남긴다. 계정 전체 기준이라 동시에 돌아간 다른 에이전트 사용량도 섞여 있다.
- **토큰 구분**: 입력(새로 처리), 출력, 캐시 읽기(이전 대화 재사용, 저렴함), 캐시 쓰기. 실제 부담은 입력과 출력, 캐시 쓰기 위주로 본다.
