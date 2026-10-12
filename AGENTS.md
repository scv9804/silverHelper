# AGENTS.md

Codex 등 AI 코딩 에이전트용 프로젝트 규칙. Claude Code는 같은 내용을 `CLAUDE.md`에서 읽는다.

## 먼저 읽을 것
1. `CLAUDE.md`: 프로젝트 구조, 명령어, 코딩 규칙, AI 파이프라인 원칙
2. `docs/workflow.md`: 팀 구성, **담당 폴더**, 작업 흐름
3. `docs/specs/`: API 명세와 공공 API 조사 결과 (있는 경우)

## 반드시 지킬 것
- 배정받은 이슈와 **담당 폴더 안에서만** 수정한다.
- `build.gradle`, `application.yml`, `db/migration/**`, `common/**`, `docs/specs/**`, `CLAUDE.md`, `AGENTS.md`는 수정하지 않는다. 필요하면 PR 설명이나 작업 보고에 "요청 사항"으로 적는다.
- API 형태는 `docs/specs/openapi.yaml`을 따른다. 바꿔야 하면 임의로 바꾸지 말고 요청한다.
- 비밀값(API 키 등)을 코드나 커밋에 넣지 않는다.
- 작업이 끝나면 아래를 통과시킨다.
  - 백엔드: `cd backend && ./gradlew test`
  - 프론트: `cd frontend && npm run lint && npm run build`

## 커밋과 PR
- 브랜치는 이미 만들어져 있다. 새 브랜치를 만들지 않는다.
- 커밋 메시지는 한국어, `feat: …` / `fix: …` / `test: …` / `docs: …`
- PR 제목은 한국어, 본문에 `Closes #이슈번호`, 변경 요약, 테스트 방법, 요청 사항(있으면)을 적는다.
- PR 생성: `gh pr create --base main --fill-first` 후 본문 보완

## 작업 보고
작업을 마치면 다음 형식으로 짧게 보고한다.
```
완료: <한 일>
PR: <링크>
테스트: <실행한 명령과 결과>
요청 사항: <공유 파일 변경 등, 없으면 없음>
```
