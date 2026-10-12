# 실버헬퍼 (silverHelper)

> 부모님이 받을 수 있는 복지 혜택을, AI가 매일 찾아 정리해 드립니다.

보호자(자녀)를 위한 시니어 복지 혜택 안내 서비스입니다.
부모님의 나이, 지역, 장기요양 등급 등을 입력하면 받을 수 있는 혜택과 신청 방법을 알려주고,
새 혜택이 생기거나 신청 마감이 다가오면 알림을 보냅니다.

이 프로젝트는 **AI CLI(Claude Code) 중심으로 개발**하며, 설계 결정과 개발 과정을 [`docs/`](docs/)에 기록합니다.

## 핵심 아이디어: AI가 매일 운영하는 데이터 파이프라인

```
[매일 새벽 Spring Batch]
 ① 수집      공공데이터 API, 지자체 공고
 ② 변경 감지  원문 해시를 비교해서 바뀐 항목만 처리 (비용 절감)
 ③ AI 추출    Claude가 JSON 스키마로 구조화하고 쉬운 말로 요약
 ④ 교차검증   GPT가 같은 원문을 검증하고, 결과가 다르면 플래그
 ⑤ 분기       신뢰도 높음 → 자동 게시 / 낮음 → 관리자 검토 대기열
 ⑥ 알림       조건이 맞는 사용자에게 새 혜택, 마감 임박 알림
```

- 정답 공급원은 **공식 데이터**이고, AI는 정리와 검증만 맡습니다.
- 모든 혜택 정보에 **출처와 기준일**을 표시합니다.

## 주요 기능 (MVP)

- [ ] 부모님 프로필 입력 (나이, 지역, 장기요양 등급, 소득 구간)
- [ ] 받을 수 있는 혜택 목록, 신청 방법, 필요 서류
- [ ] 일일 수집·AI 정리 파이프라인 + 관리자 검토 대시보드
- [ ] 새 혜택, 마감 임박 알림
- [ ] 혜택 질문 챗봇 (수집한 데이터 안에서만 답변하는 RAG)
- [ ] 카카오/구글 로그인

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Data JPA, Spring Batch, Spring Security (OAuth2) |
| AI | Spring AI, Anthropic Claude, OpenAI GPT |
| DB | PostgreSQL + pgvector, Redis |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, TanStack Query, Zustand |
| Infra | Docker, AWS (EC2, RDS, ECR, S3/CloudFront), GitHub Actions |

선정 이유는 [ADR 0001](docs/adr/0001-tech-stack.md)에 있습니다.

## 프로젝트 구조

```
silverHelper/
├─ backend/              Spring Boot API + 배치
├─ frontend/             React 웹
├─ infra/                운영 배포 설정
├─ docs/
│  ├─ adr/               설계 결정 기록
│  └─ devlog/            개발일지 (AI와 어떻게 개발했는지)
├─ docker-compose.yml    로컬 개발용 DB/Redis
└─ CLAUDE.md             AI 에이전트용 프로젝트 가이드
```

## 로컬 실행

**필요한 것:** JDK 21, Node.js 22.12 이상, Docker Desktop

```bash
# 1. 환경변수
cp .env.example .env

# 2. DB, Redis 실행
docker compose up -d

# 3. 백엔드 (http://localhost:8080)
cd backend && ./gradlew bootRun

# 4. 프론트엔드 (http://localhost:5173)
cd frontend && npm install && npm run dev
```

## AI CLI 개발 방식

- 이슈 1개 = 브랜치 1개 = PR 1개로 작업하고, PR마다 AI 리뷰를 거칩니다.
- [`CLAUDE.md`](CLAUDE.md)에 코딩 규칙과 AI 파이프라인 원칙을 정의해 에이전트가 일관되게 작업하도록 합니다.
- 개발 과정에서 AI에게 맡긴 것과 사람이 직접 판단·수정한 것을 [`docs/devlog/`](docs/devlog/)에 기록합니다.

## 라이선스

MIT
