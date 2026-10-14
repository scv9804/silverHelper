# 단일 서버 배포 절차

로컬과 AWS는 동일한 `infra/docker-compose.prod.yml`, 백엔드·프론트 이미지 빌드 정의, PostgreSQL/pgvector, Redis를 쓴다. 이 PC에서는 포트폴리오 Apache2.4가 80 포트를 사용하므로 운영 프로필을 HTTP 8088로 검증한다. Apache는 중지하거나 변경하지 않는다. AWS에서는 `infra/docker-compose.https.yml`을 추가해 도메인 인증서와 HTTPS를 켠다. 이 문서는 절차이며 AWS 계정 생성, 결제, DNS 설정 및 실제 배포는 수행하지 않는다.

이전 ECR/RDS/S3/CloudFront 배포용 `.github/workflows/deploy.yml`은 팀장이 `main`에서 삭제했다. **새 단일 서버 자동 배포는 미설정**이며 아래 AWS 이전 절차는 사람이 직접 수행하는 수동 절차다.

## 이 PC에서 로컬 호스팅

Docker Desktop Linux 엔진을 실행한 뒤 저장소 루트에서 PowerShell로 한 줄 실행한다. 최초 실행 시 `infra/.env`에 로컬 DB/JWT 난수를 생성하고, 이후 실행은 그 파일을 유지한다. 비밀값 파일은 Git에 올리지 않는다.

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\start-local.ps1
```

스크립트는 Compose 문법 검사, 이미지 빌드, 컨테이너 기동, `/actuator/health` 확인을 수행한다. 이 PC의 기본 주소는 `http://localhost:8088`이다. 80 포트가 비어 있는 다른 PC에서 `http://<PC IP>`로 제공하려면 `infra/.env`의 `HTTP_PORT=80`으로 설정하고 다시 실행한다. 재실행과 종료 명령:

```powershell
docker compose -f infra/docker-compose.prod.yml --project-directory infra ps
docker compose -f infra/docker-compose.prod.yml --project-directory infra down
```

같은 Wi-Fi의 휴대폰에서는 이 PC의 Wi-Fi IPv4 주소를 확인해 `http://<PC IPv4>:8088`로 접속한다. 80 포트가 비어 `HTTP_PORT=80`으로 실행한 다른 PC에서는 `http://<PC IPv4>`로 접속한다. PowerShell에서 `Get-NetIPAddress -AddressFamily IPv4`로 주소를 확인한다. Windows 방화벽에서 **개인 네트워크 TCP 8088 인바운드**(80 설정 시 80)를 허용한다. 공용 네트워크 프로필이나 공유기 포트 포워딩은 사용하지 않는다. 휴대폰과 PC의 서브넷이 같아야 하며, Wi-Fi의 클라이언트 격리 기능이 켜져 있으면 접속할 수 없다. 방화벽 규칙은 테스트 후 필요 없으면 제거한다.

운영 프로필이므로 개발 로그인과 샘플 시드는 꺼져 있다. AI/API 키가 없으면 공개 혜택이 0건일 수 있다. 화면 확인에 데모 데이터가 필요하면 운영 DB를 바꾸지 말고 별도 `demo` 프로필과 명확한 “데모” 표시를 별도 이슈로 구현한다. 로컬 HTTP의 IP 주소에서는 `Secure` 쿠키와 외부 OAuth 로그인 기능이 브라우저 정책상 제한될 수 있다. 모바일에서는 우선 페이지, 목록, 레이아웃, API 응답을 검증한다.

## AWS Lightsail 또는 EC2 한 대로 이전

1. 사용자가 Linux 인스턴스와 고정 공인 IP를 준비한다. OS 패치, Docker Engine과 Compose 플러그인을 설치한다. 서버 방화벽과 Lightsail/EC2 보안 그룹은 SSH(관리자 IP만), HTTP 80, HTTPS 443만 열고 PostgreSQL 5432, Redis 6379, 백엔드 8080은 공개하지 않는다.
2. 저장소의 고정된 커밋을 서버에 체크아웃한다. `infra/.env.example`을 `infra/.env`로 복사해 DB/JWT 난수와 실제 서비스 설정을 채우고, `HTTP_PORT=80`으로 설정한다. 파일은 소유자만 읽도록 `chmod 600 infra/.env`로 제한한다. 로컬의 운영 DB를 이전해야 하면 [운영 체크리스트](../infra/OPERATIONS.md)의 백업을 외부의 안전한 경로로 전달해 복구한다. DB 이름/사용자는 양쪽에서 일치시킨다.
3. 사용자가 도메인 A 레코드를 고정 공인 IP로 연결하고, 80 포트 접근을 확인한다. 인증서 발급에는 해당 도메인의 소유권과 공개 HTTP 접근이 필요하다. `certbot certonly --standalone -d <domain>` 등으로 Let's Encrypt 인증서를 발급한다. 최초 발급 시 80 포트 점유 서비스는 잠시 중지한다. 인증서 파일을 호스트의 전용 디렉터리에 `fullchain.pem`, `privkey.pem` 이름으로 준비하고 `TLS_CERT_DIR`에 **절대 경로**를 적는다. Docker가 읽을 수 있는 권한을 설정하고 개인키는 외부에 공개하지 않는다. 갱신 후 `docker compose ... exec web nginx -s reload`가 실행되도록 갱신 훅을 설정한다.
4. `AUTH_CALLBACK_URL=https://<domain>/auth/callback`을 `.env`에 적고 Google/Kakao를 사용한다면 공급자 콘솔의 리다이렉트 URI를 `https://<domain>/login/oauth2/code/<provider>`로 등록한다. HTTPS에서만 브라우저 로그인과 쿠키 동작을 검증한다.
5. `infra/`에서 아래 명령으로 HTTPS 추가 구성을 검사하고 동일한 백엔드·프론트 이미지를 빌드/실행한다. `docker compose config` 전체 출력은 비밀값을 포함하므로 출력하거나 저장하지 않는다.

```sh
cd infra
docker compose -f docker-compose.prod.yml -f docker-compose.https.yml config --quiet
docker compose -f docker-compose.prod.yml -f docker-compose.https.yml up --build -d
docker compose -f docker-compose.prod.yml -f docker-compose.https.yml ps
docker compose -f docker-compose.prod.yml -f docker-compose.https.yml exec web nginx -t
curl -I http://<domain>/
curl -I https://<domain>/
curl https://<domain>/actuator/health
```

HTTP는 HTTPS로 리다이렉트되고, health는 `UP`이어야 한다. 이어서 [운영 체크리스트](../infra/OPERATIONS.md)의 Swagger, 개발 로그인, API, 백업 항목을 확인한다. 무중단 배포나 고가용성은 이 한 대 구성의 범위에 없다. 컨테이너 이미지는 같은 Dockerfile로 빌드하며, 공급자 전용 레지스트리나 서비스는 필요하지 않다.
