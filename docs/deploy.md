# AWS 배포 준비

실제 배포는 계정과 비용 승인을 받은 뒤 사람이 수행한다. `.github/workflows/deploy.yml`은 main의 CI 성공 후 실행되지만 필요한 GitHub 시크릿이 없으면 해당 배포 작업을 건너뛴다. 수동 실행도 main에서만 허용한다.

## 1. 계정과 네트워크

1. AWS 계정의 비용 알림과 예산을 설정하고 사용할 리전을 정한다.
2. VPC의 비공개 서브넷에 RDS PostgreSQL 17을 준비한다. EC2의 보안 그룹에서만 DB 포트 5432를 허용한다. pgvector 확장 설치 가능 여부를 확인한다. 운영 DB 계정에 `CREATE EXTENSION vector` 권한을 준비하거나 관리자가 V5 실행 전에 확장을 설치한다.
3. EC2에는 Docker Engine, Compose 플러그인, AWS CLI, SSM Agent를 설치한다. HTTPS는 로드 밸런서 또는 역방향 프록시에서 종료한다. EC2 보안 그룹은 필요한 인바운드만 열고 RDS에 접근할 수 있게 한다.
4. ECR 리포지토리를 만들고 EC2 인스턴스 역할에 ECR pull과 SSM 관리 권한을 부여한다. EC2의 `/opt/silverhelper`에 저장소의 `infra/docker-compose.prod.yml`을 배치한다.

## 2. 백엔드 환경값

EC2의 `/opt/silverhelper/.env`는 파일 권한을 제한해 사람이 만든다. `.env.example`을 기준으로 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST=redis`, `JWT_SECRET`(32바이트 이상)을 설정한다. 이 파일은 Git에 넣지 않는다. Compose의 `BACKEND_IMAGE`는 워크플로가 실행 시 전달한다. DB 접속과 Flyway V1~V5 적용을 운영 전 별도 환경에서 검증한다.

OAuth 제공자 콘솔에서 Google/Kakao 앱과 리디렉션 URI를 등록하고 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `AUTH_CALLBACK_URL`을 설정한다. SMTP 계정과 `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM`을 준비한다. `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`는 실제 AI 어댑터를 쓰기로 결정한 경우에만 입력한다. 키가 없으면 가짜 구현 표시가 유지되어야 한다.

관리자 지정은 가입된 회원 ID를 확인하고 운영자가 DB에서 `user_account.role='ADMIN'`으로 변경한다. 변경자, 시간, 대상 ID를 운영 기록에 남기고 `/api/admin/**` 접근을 확인한다. 이메일 주소만으로 역할을 부여하지 않는다.

## 3. 배포용 GitHub 권한과 시크릿

GitHub Actions OIDC 제공자를 AWS IAM에 등록한다. 저장소와 main 브랜치로 trust policy를 제한한 배포 역할에 다음 최소 권한을 준다: ECR push, 대상 EC2 인스턴스에 대한 SSM SendCommand/GetCommandInvocation, 대상 S3 버킷 쓰기·삭제, 대상 CloudFront 배포 무효화. AWS 정적 액세스 키는 사용하지 않는다.

저장소 Secrets에 `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_REPOSITORY`, `EC2_INSTANCE_ID`, `S3_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`를 등록한다. 백엔드와 프론트의 필수값은 독립적으로 판별한다. 모두 등록해도 main CI가 성공하기 전에는 자동 배포되지 않는다.

## 4. 프론트와 확인

S3 버킷은 비공개로 두고 CloudFront Origin Access Control로 제공한다. SPA 경로가 새로고침에서 동작하도록 403/404 오류를 `/index.html`로 매핑한다. CloudFront에서 HTTPS 도메인을 연결하고 `/api`는 백엔드 HTTPS 엔드포인트로 프록시하거나 별도 API 도메인을 구성한다. OAuth callback URL과 CORS/쿠키 도메인이 실제 도메인과 일치하는지 확인한다.

CI 통과 후 워크플로의 ECR push, SSM 배포, S3 업로드 및 CloudFront 무효화 결과를 확인한다. `/actuator/health`, 로그인·갱신·로그아웃, 공개 혜택 조회, 관리자 권한, Mailpit 대신 실제 SMTP 발송, 챗봇 출처와 가짜 표시를 순서대로 점검한다. 문제가 생기면 이전 커밋의 ECR 이미지 URI로 EC2 Compose를 재실행하고 이전 프론트 배포본을 복원한다.
