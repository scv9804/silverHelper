# 작업 지시서: Phase 2 (2026-10-14)

- 지시: 기획 세션(사용자 + Claude) → Codex 팀장 `jaewon`
- 사용자 지시: **Codex 한도가 넉넉하니 Phase 2는 Codex로 만든다.** 중간 확인 없이 끝까지 진행하고 마지막에 한 번 보고한다.
- 범위: 로그인 → 회원·프로필 저장 → 관리자 검토 화면 → 알림 → 챗봇(RAG) → 배포 준비. **AWS에 실제로 배포하지 않는다**(계정·비용 필요). 이슈 #7 후속 과제 중 코드 작업도 포함한다.

## 팀 (Phase 2는 전원 Codex)

| herdr 이름 | 이름 | 모델 | 역할 | 작업 위치 |
|---|---|---|---|---|
| `jaewon` | 박재원 팀장 | Codex | 명세·마이그레이션·이슈 작성, 작업자 지시, Codex 리뷰, 머지 준비 | 메인 저장소 (`main`) |
| `seojun` | 김서준 | Codex | 백엔드: 인증, 회원, 부모님 프로필, 관리자 권한 | worktree |
| `haeun` | 최하은 | Codex | 프론트: 로그인, 마이페이지, 관리자 검토 화면, 챗봇 화면 | worktree |
| `minjun` | 최민준 | Codex | 백엔드: 알림, 챗봇(RAG), 이슈 #7 파이프라인 개선 | worktree |

- Codex 리뷰: 작성자가 아닌 다른 Codex 작업자가 리뷰한다(새 맥락으로 보게 하기 위해).
- **Claude 최종 리뷰**: 머지 직전에 Claude(`doyun`)가 한 번 리뷰한다. 모델 간 교차 리뷰를 유지하기 위한 단계로, 기획 세션이 요청한다. 팀장은 "Claude 리뷰 대기" 상태로 보고만 하면 된다.

## 외부 키가 필요한 것 (지금은 없음)
| 기능 | 필요한 것 | 지금 할 일 |
|---|---|---|
| 카카오·구글 로그인 | OAuth 클라이언트 ID/시크릿 | 설정 자리만 만들고, `local` 프로필에서만 켜지는 **개발용 로그인**(`/api/dev/login`)으로 테스트 |
| 알림 메일 | SMTP 계정 | docker-compose에 **Mailpit**(개발용 메일 서버)을 추가해 로컬에서 확인 |
| 챗봇 임베딩·답변 | `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | 인터페이스 + 가짜 구현. 키가 있을 때만 실제 구현. **가짜 결과임을 응답에 표시** |

새로 필요한 환경변수는 `.env.example`에 빈 값으로 추가한다.

## 순서

### P2-0 팀장 혼자 (계약 확정)
1. `docs/adr/0003-auth.md`: Spring Security OAuth2 Login(카카오·구글) → 자체 JWT(액세스 + 리프레시, 리프레시는 Redis), 역할 `USER`/`ADMIN`, 관리자 지정 방법, 개발용 로그인
2. `docs/adr/0004-notification-rag.md`: 알림(새 혜택·마감 임박, 조건 매칭 → 메일, 중복 발송 방지), 챗봇(pgvector, 공개된 혜택만 근거로 답변, 출처 표시, 근거 없으면 "모름")
3. 마이그레이션: `V3__users.sql`(회원, 소셜 계정, 부모님 프로필, 관심 혜택), `V4__notification.sql`, `V5__rag.sql`(pgvector 확장과 임베딩 테이블). docker-compose의 이미지는 이미 pgvector다
4. `docs/specs/openapi.yaml` 갱신: 인증, 내 정보, 부모님 프로필 CRUD, 알림 설정, 챗봇
5. `build.gradle`, `application.yml`, `docker-compose.yml`, `.env.example` 공유 파일 변경
6. 이슈 작성: 작업마다 담당자, 담당 폴더, 완료 조건. 라벨 `phase:2`, `agent:<이름>`

### P2-1 동시 개발
| 브랜치 | 담당 | 내용 |
|---|---|---|
| `feat/auth` | seojun | OAuth2 로그인, JWT, 개발용 로그인, 내 정보, 부모님 프로필 저장, 관리자 API 보호 |
| `feat/web-auth-admin` | haeun | 로그인·마이페이지(저장한 프로필로 매칭), 관리자 검토 대기열 화면(승인·반려), 챗봇 화면 |
| `feat/notification-rag` | minjun | 알림 배치와 메일 발송, 챗봇 API(RAG), 이슈 #7 코드 항목 |

### P2-2 배포 준비 (팀장)
- GitHub Actions 배포 워크플로: 백엔드 이미지 빌드 → ECR push → EC2 배포. **시크릿이 없으면 건너뛰도록**(`if:` 조건) 만들어 지금 CI가 깨지지 않게 한다
- 프론트 빌드 → S3/CloudFront 업로드 단계도 같은 방식
- `docs/deploy.md`: 사람이 해야 할 AWS 준비(계정, IAM, ECR, EC2, RDS, 시크릿 등록)와 순서

## 운영 규칙
- worktree 경로 `D:/workspace/silverHelper-wt/<이름>`
- 작업자 실행: `herdr agent start <이름> --kind codex --pane <id> -- --approve-for-me`
- **사용량 기록은 지시하는 쪽(팀장)이 한다**: 지시를 보내기 직전 `node scripts/track.mjs start <ID> <이름> codex "설명"`(작업자 worktree에서), 보고를 받은 직후 `end`. ID는 `P2-<이름>`, 리뷰는 `R-PR<번호>`
- 담당 폴더와 공유 파일 규칙은 `docs/workflow.md`와 같다. 공유 파일은 팀장만 수정
- 리뷰와 수정은 PR 하나당 최대 3회. 마지막 확인에서는 "잘못된 정보 공개, 데이터 손상, 보안 문제"만 막고 나머지는 후속 이슈로
- 승인 요청은 프로젝트 안의 빌드·테스트·git·gh·docker compose 명령만 승인. 프로젝트 밖 삭제나 시스템 변경은 거절
- 비밀값은 커밋하지 않는다. 테스트용 JWT 키 등은 테스트 리소스에서 생성

## 끝나면 보고
`docs/devlog/2026-10-14.md`에 정리하고 다음 형식으로 보고한다.
- 완료한 작업과 PR 링크, 각 PR의 리뷰 상태(Codex 리뷰 완료 / Claude 리뷰 대기)
- 사용량 표(ledger에서 Phase 2만)
- 사람이 해야 할 일 (OAuth 앱 등록, SMTP, AI 키, AWS)
- 문제와 해결 과정 (면접 소재)
