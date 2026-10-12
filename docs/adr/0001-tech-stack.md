# 0001. 기술 스택 선정

- 상태: 승인
- 날짜: 2026-10-13

## 배경
1인 개발, AI CLI(Claude Code) 중심 개발, 국내 채용시장에서 통용되는 포트폴리오가 목표다.

## 결정
- 백엔드: Java 21, Spring Boot 4, Spring Data JPA, Spring Batch, Spring Security(OAuth2)
- AI: Spring AI (Anthropic Claude + OpenAI GPT)
- DB: PostgreSQL + pgvector (RAG 벡터 검색을 같은 DB에서 처리), 캐시 Redis
- 프론트: React + TypeScript + Vite + Tailwind, TanStack Query, Zustand
- 인프라: Docker, AWS(EC2, RDS, ECR, S3/CloudFront), GitHub Actions

## 이유
- Spring Boot + React + AWS는 국내 백엔드/풀스택 채용에서 가장 많이 요구되는 조합이다.
- 매일 수집하는 파이프라인은 재시작과 실행 이력이 필요해서 Spring Batch가 맞다.
- Spring AI로 두 모델을 같은 인터페이스로 호출하면 교차검증 구현이 단순해진다.

## 결과
- 개발 중에는 DB/Redis만 컨테이너로 띄우고 앱은 로컬에서 실행한다.
- 배포는 백엔드 Docker 이미지를 ECR에 올려 EC2에서 실행한다.
