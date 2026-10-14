# 단일 서버 배포 절차

로컬과 AWS는 동일한 `infra/docker-compose.prod.yml`, 백엔드·프론트 이미지 빌드 정의, PostgreSQL/pgvector, Redis를 쓴다. 이 PC에서는 포트폴리오 Apache2.4가 80 포트를 사용하므로 운영 프로필을 HTTP 8088로 검증한다. Apache는 중지하거나 변경하지 않는다. AWS에서는 `infra/docker-compose.https.yml`을 추가해 도메인 인증서와 HTTPS를 켠다. 이 문서는 절차이며 AWS 계정 생성, 결제, DNS 설정 및 실제 배포는 수행하지 않는다.

이전 ECR/RDS/S3/CloudFront 배포용 `.github/workflows/deploy.yml`은 팀장이 `main`에서 삭제했다. **새 단일 서버 자동 배포는 미설정**이며 아래 AWS 이전 절차는 사람이 직접 수행하는 수동 절차다.

## 이 PC에서 로컬 호스팅

Docker Desktop Linux 엔진을 실행한 뒤 저장소 루트에서 PowerShell로 한 줄 실행한다. 최초 실행 시 `infra/.env`에 로컬 DB/JWT 난수를 생성하고, 이후 실행은 그 파일을 유지한다. 비밀값 파일은 Git에 올리지 않는다.

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\start-local.ps1
```

스크립트는 Compose 문법 검사, 이미지 빌드, 컨테이너 기동, `/actuator/health` 확인을 수행한다. 이 PC의 기본 주소는 `http://localhost:8088`이며, `AUTH_CALLBACK_URL`도 `http://localhost:8088/auth/callback`으로 만든다. 기존 `.env`의 localhost callback도 실행 시 `HTTP_PORT`에 맞춰 갱신한다. 80 포트가 비어 있는 다른 PC에서 `http://<PC IP>`로 제공하려면 `infra/.env`의 `HTTP_PORT=80`으로 설정하고 다시 실행한다. localhost가 아닌 callback을 직접 입력했다면 그 URL의 포트도 `HTTP_PORT`와 같아야 한다. 재실행과 종료 명령:

```powershell
docker compose -f infra/docker-compose.prod.yml --project-directory infra ps
docker compose -f infra/docker-compose.prod.yml --project-directory infra down
```

같은 Wi-Fi의 휴대폰에서는 이 PC의 Wi-Fi IPv4 주소를 확인해 `http://<PC IPv4>:8088`로 접속한다. 80 포트가 비어 `HTTP_PORT=80`으로 실행한 다른 PC에서는 `http://<PC IPv4>`로 접속한다. PowerShell에서 `Get-NetIPAddress -AddressFamily IPv4`로 주소를 확인한다. Windows 방화벽에서 **개인 네트워크 TCP 8088 인바운드**(80 설정 시 80)를 허용한다. 공용 네트워크 프로필이나 공유기 포트 포워딩은 사용하지 않는다. 휴대폰과 PC의 서브넷이 같아야 하며, Wi-Fi의 클라이언트 격리 기능이 켜져 있으면 접속할 수 없다. 방화벽 규칙은 테스트 후 필요 없으면 제거한다.

운영 프로필이므로 개발 로그인과 샘플 시드는 꺼져 있다. AI/API 키가 없으면 공개 혜택이 0건일 수 있다. 화면 확인에 데모 데이터가 필요하면 운영 DB를 바꾸지 말고 별도 `demo` 프로필과 명확한 “데모” 표시를 별도 이슈로 구현한다. 로컬 HTTP의 LAN IP에서는 `Secure` refresh 쿠키와 외부 OAuth 공급자의 HTTP 리다이렉트 정책 때문에 로그인·갱신이 제한될 수 있다. 휴대폰에서는 페이지, 목록, 레이아웃, 공개 API를 검증하고 로그인·갱신·로그아웃은 HTTPS 단계에서 확인한다. LAN IP로 OAuth를 별도 시험하려면 `AUTH_CALLBACK_URL`의 호스트를 해당 IP로 변경하고 공급자 리다이렉트 허용 여부를 확인한다.

## AWS Lightsail 또는 EC2 한 대로 이전

1. 사용자가 Linux 인스턴스와 고정 공인 IP를 준비한다. OS 패치, Docker Engine과 Compose 플러그인을 설치한다. 서버 방화벽과 Lightsail/EC2 보안 그룹은 SSH(관리자 IP만), HTTP 80, HTTPS 443만 열고 PostgreSQL 5432, Redis 6379, 백엔드 8080은 공개하지 않는다.
2. 저장소의 고정된 커밋을 서버에 체크아웃한다. `infra/.env.example`을 `infra/.env`로 복사해 DB/JWT 난수와 실제 서비스 설정을 채우고, `HTTP_PORT=80`으로 설정한다. 파일은 소유자만 읽도록 `chmod 600 infra/.env`로 제한한다. 로컬의 운영 DB를 이전해야 하면 [운영 체크리스트](../infra/OPERATIONS.md)의 백업을 외부의 안전한 경로로 전달해 복구한다. DB 이름/사용자는 양쪽에서 일치시킨다.
3. 사용자가 도메인 A 레코드를 고정 공인 IP로 연결하고, 80 포트 접근을 확인한다. Certbot은 **webroot HTTP-01** 방식을 쓴다. `.env`에 `ACME_WEBROOT_DIR=/var/lib/silverhelper/acme`, `TLS_CERT_DIR=/etc/letsencrypt`처럼 절대 경로를 적고, 호스트에서 `mkdir -p /var/lib/silverhelper/acme/.well-known/acme-challenge`로 만든다. 최초 발급 전에는 아래 HTTP 부트스트랩 구성을 실행한다. nginx가 80을 계속 사용하므로 Certbot이 별도로 포트를 바인딩하지 않는다.

   ```sh
   cd infra
   docker compose -f docker-compose.prod.yml -f docker-compose.acme.yml config --quiet
   docker compose -f docker-compose.prod.yml -f docker-compose.acme.yml up --build -d
   printf 'acme-check\n' > /var/lib/silverhelper/acme/.well-known/acme-challenge/probe
   curl --fail --silent --show-error http://<domain>/.well-known/acme-challenge/probe
   rm /var/lib/silverhelper/acme/.well-known/acme-challenge/probe
   sudo certbot certonly --webroot -w /var/lib/silverhelper/acme -d <domain>
   sudo ln -s <domain> /etc/letsencrypt/live/current
   ```

   `curl` 결과가 `acme-check`가 아니면 발급 전에 DNS·80 포트·webroot 마운트를 고친다. `current`는 `live/<domain>`을 가리키는 상대 심볼릭 링크다. `TLS_CERT_DIR`에는 Certbot의 **전체** `/etc/letsencrypt` 트리를 마운트해야 `live`에서 `archive`로 이어지는 인증서 심볼릭 링크가 컨테이너 안에서도 해석된다. 인증서 개인키와 디렉터리 권한은 Docker가 읽을 수 있게 하되 외부에 공개하지 않는다.
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

## 인증서 갱신과 실패 복구

최초 발급에 사용한 webroot 경로는 Certbot 갱신 설정에 저장된다. 실행 가능한 deploy hook을 Certbot의 hook 디렉터리에 연결하면 시스템의 Certbot timer가 `renew`를 실행할 때 성공한 갱신에 한해 nginx 설정 검사와 reload를 수행한다. 스크립트의 절대 경로는 서버의 저장소 경로로 바꾼다. `standalone`은 사용하지 않으므로 nginx의 80 포트를 비울 필요가 없다. [Certbot 갱신 안내](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates)를 따른다.

```sh
sudo install -d /etc/letsencrypt/renewal-hooks/deploy
sudo ln -s /absolute/path/to/silverHelper/infra/reload-renewed-cert.sh /etc/letsencrypt/renewal-hooks/deploy/silverhelper-reload
sudo certbot renew --cert-name <domain> --dry-run --run-deploy-hooks
sudo systemctl list-timers '*certbot*'
```

`--run-deploy-hooks`가 붙은 dry run은 현재 유효한 인증서로 reload hook까지 시험한다. timer가 없다면 `sudo certbot renew`를 주기적으로 실행하도록 별도 cron/systemd timer를 등록한다. 갱신 전 `/etc/letsencrypt` 전체를 권한을 제한한 외부 백업에 보관한다(개인키 포함). 갱신 시 Certbot은 `live/<domain>`의 링크를 새 `archive` 파일로 갱신하고, `live/current`는 그대로 해당 디렉터리를 가리킨다. nginx는 reload 후 새 인증서를 읽는다. `openssl s_client -connect <domain>:443 -servername <domain> </dev/null 2>/dev/null | openssl x509 -noout -enddate`로 제공 중인 인증서 만료일을 확인한다.

`--dry-run`이 실패하면 기존 인증서는 그대로 두고 challenge URL, 공개 80 포트, DNS와 Certbot 로그를 확인한 뒤 재시험한다. deploy hook의 `nginx -t`가 실패하면 reload를 하지 않아 기존 nginx worker가 이전 인증서를 계속 제공한다. 컨테이너를 재시작하지 말고 설정·권한 문제를 고쳐 hook을 직접 재실행한 후 만료일을 확인한다. 복구가 불가능한 경우 백업한 Certbot 트리(개인키 포함)를 관리자 권한으로 복원하고 `nginx -t` 성공 후 reload한다. Certbot은 deploy hook 실패를 항상 전체 명령의 실패 코드로 전달하지 않으므로 hook 로그와 실제 만료일도 모니터링한다.
