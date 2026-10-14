# 이슈 #7 혜택 API 성능 후속 측정

실제 공공 데이터 적재와 Docker 엔진이 준비된 뒤 수행한다. 빈 DB나 테스트 소량 데이터의 수치는 사용하지 않는다.

1. `docker compose up -d` 후 실제 수집 배치를 완료한다.
2. `select count(*) from benefit where status='ACTIVE' and verified_hash is not null;`로 공개 건수를 기록한다.
3. PostgreSQL에서 `ALTER SYSTEM SET track_io_timing = on; SELECT pg_reload_conf();` 및 `pg_stat_statements`를 활성화한다. 설정 권한이 없으면 애플리케이션 JDBC 로그로 쿼리 수를 센다.
4. 인증된 사용자로 `GET /api/benefits?page=0&size=20` 및 `POST /api/benefits/match?page=0&size=20`을 동일한 프로필로 각각 20회 호출한다. 첫 5회는 워밍업으로 버리고 나머지 15회의 p50/p95 응답 시간과 요청당 SQL 수를 기록한다.
5. 같은 조건의 SQL에 `EXPLAIN (ANALYZE, BUFFERS)`를 실행해 순차 스캔과 읽은 블록 수를 확인한다. 건수, 장비, 파라미터, 쿼리 수, p50/p95, 실행 계획을 후속 이슈에 첨부한다.

측정 대상 코드는 benefit 담당 폴더이므로 이 브랜치에서 성능을 임의로 수정하지 않는다.
