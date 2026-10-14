# 단일 서버 운영 체크리스트

로컬 실행과 AWS HTTPS 순서는 [배포 절차](../docs/deploy.md)에 있다. 운영 Compose는 `prod` 프로필만 활성화한다.

## 시작 전

- [ ] `infra/.env`를 버전 관리 밖에 두고 접근 권한을 제한한다. `DB_PASSWORD`는 임의의 긴 값, `JWT_SECRET`은 최소 32바이트의 독립 난수로 설정한다. 키를 프론트 빌드 변수에 넣지 않는다.
- [ ] DB/Redis/backend에는 호스트 포트를 공개하지 않는다. 호스트에서 공개하는 포트는 HTTP 80, HTTPS 구성 시 443뿐이다.
- [ ] 운영 설정의 `SPRING_PROFILES_ACTIVE=prod`를 확인한다. `local` 전용 개발 로그인과 샘플 시드는 운영에서 생성되지 않아야 한다. `/api/dev/login` 호출이 성공하지 않는지 확인한다.
- [ ] Swagger UI와 API 문서는 환경변수로 비활성화하고, nginx에서도 차단한다. `/swagger-ui.html`, `/v3/api-docs`가 404인지 확인한다.
- [ ] 프론트와 API를 같은 출처(`/api`)로 제공한다. CORS 와일드카드를 추가하지 않는다. OAuth 공급자 리다이렉트 URI와 `AUTH_CALLBACK_URL`을 실제 공개 origin에 맞춘다.
- [ ] 로컬 HTTP에서는 `prod`의 refreshToken 쿠키가 `Secure; HttpOnly; SameSite=Lax`이므로 브라우저별 HTTP/IP 로그인 동작을 검증한다. 모바일 Wi-Fi HTTP에서는 OAuth·refresh 쿠키가 동작하지 않을 수 있다. 실제 로그인 제공은 HTTPS 단계에서 검증한다.
- [ ] HTTPS 단계에서 유효한 도메인 인증서를 장착하고 HTTP→HTTPS 전환, `Secure` 쿠키, OAuth callback, 브라우저 네트워크 요청에 혼합 콘텐츠가 없는지 확인한다.
- [ ] SMTP와 외부 API/AI 키가 필요하면 `.env`에 입력하고 기능을 개별 검증한다. 로컬 운영 프로필에는 개발 샘플 데이터가 자동으로 생기지 않는다.

## 검증 명령

아래 명령은 `infra/`에서 실행한다. 이 PC의 `BASE_URL`은 8088이며, 80 포트가 비어 `HTTP_PORT=80`으로 실행한 서버에서는 `http://localhost`로 바꾼다. `docker compose config`는 비밀값을 출력하므로 `--quiet`만 사용한다.

```sh
docker compose -f docker-compose.prod.yml config --quiet
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml exec web nginx -t
BASE_URL=http://localhost:8088
curl -i "$BASE_URL/"
curl -i "$BASE_URL/actuator/health"
curl -i "$BASE_URL/api/benefits"
curl -i "$BASE_URL/swagger-ui.html"
curl -i "$BASE_URL/v3/api-docs"
curl -i -X POST "$BASE_URL/api/dev/login" -H 'Content-Type: application/json' -d '{}'
```

정상 조건: 홈은 200 및 프론트 HTML, health는 `UP`, `/api/benefits`는 API 응답, Swagger 둘은 404, 개발 로그인은 성공 응답이 아니다. 개발 로그인 경로의 401/403/404/405는 모두 허용할 수 있지만 2xx면 배포를 중단한다. 콘텐츠가 비어 있어도 샘플 시드로 운영 DB를 채우지 않는다.

## DB 백업·복구

매일 `pg_dump` 사용자 정의 형식으로 백업하고, 서버 밖의 암호화된 저장소로 복사한다. 복구 절차를 별도 테스트 DB에서 정기 검증한다. 아래 예시는 Linux 호스트에서 `infra/` 디렉터리 기준이며, 백업 파일은 저장소 밖에 둔다.

```sh
mkdir -p "$HOME/silverhelper-backups"
docker compose -f docker-compose.prod.yml exec -T postgres sh -c 'exec pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"' > "$HOME/silverhelper-backups/silverhelper-$(date +%F).dump"
```

복구는 서비스 중지·백업 보관 후에만 수행한다. 대상 DB를 새로 만든 빈 DB로 준비하고, 백업을 표준 입력으로 전달한다. 운영 데이터에 직접 덮어쓰기 전 테스트 서버에서 검증한다.

```sh
docker compose -f docker-compose.prod.yml exec -T postgres sh -c 'exec pg_restore --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /path/to/verified-backup.dump
```

Redis는 refresh token 등 임시 상태를 보관한다. Redis 볼륨 손실 시 기존 로그인 세션이 끊길 수 있다. PostgreSQL 볼륨과 백업 삭제 명령(`down -v`)은 운영에서 사용하지 않는다.
