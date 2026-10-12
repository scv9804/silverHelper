# 공공 API 실제 응답 픽스처

2026-10-13 P0-1b에서 실제 호출로 받은 응답이다. 인증키는 들어 있지 않다. 목록은 3건으로 줄였다(구조는 원본 그대로).
설명은 `docs/specs/public-api.md` 참고.

| 파일 | 원천 | 내용 |
|---|---|---|
| `national-list.xml` | A 중앙부처 목록 | 노년 1건, 생애주기 없는 1건, 제공유형 여러 개인 1건 |
| `national-detail-WLF00001177.xml` | A 상세 | 장기요양 본인부담금 감경 |
| `national-detail-WLF00001164.xml` | A 상세 | 기초연금 |
| `national-error-invalid-param.xml` | A | `srchKeyCode` 누락 → `resultCode=10` (정상 구조, `wantedList`) |
| `gateway-error-unregistered-key.xml` | A·B 게이트웨이 | 잘못된 키 → `OpenAPI_ServiceResponse` 구조 |
| `local-list.xml` | B 지자체 목록 | 노년+시군구 1건, 시군구 없는 1건, 생애주기 없는 1건 |
| `local-detail-WLF00002281.xml` | B 상세 | 보성군 차상위 노인 장기요양보험료 지원 (`enfcEndYmd=99991231`) |
| `gov24-list.json` | C 목록 | 장기요양 감경, 시군구 어르신 사업, 법인 대상 섞인 사업 |
| `gov24-conditions.json` | C 지원조건 | 위 3건의 `supportConditions` |
| `gov24-detail-135200005031.json` | C 상세 | 장기요양급여비용 본인부담금 감경 |
| `gov24-error-invalid-key.json` | C | 잘못된 키 |
