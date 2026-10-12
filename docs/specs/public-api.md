# 공공 복지서비스 API 조사 (Phase 0-1)

- 작성: doyun, 2026-10-13
- 목적: 수집 파이프라인(`pipeline`)과 DB 스키마(0-2)의 근거 자료
- 표기
  - **[확인]** 공식 자료(포털 페이지, Swagger, 제공기관 활용가이드)나 실제 호출로 확인한 사실. 출처를 함께 적는다.
  - **[추측]** 문서에 없거나 모호해서 추정한 내용. 인증키 발급 후 실제 호출로 검증해야 한다.
- P0-1 시점에는 인증키가 없어 공식 샘플만 봤다. **P0-1b(2026-10-13)에 실제 키로 호출해 검증했고 결과는 §10에 있다.** 본문의 [추측] 중 검증된 것은 [확인]으로 바꾸고 `(P0-1b)`를 붙였다.
- 실제 응답 샘플: `backend/src/test/resources/fixtures/public-api/` (인증키 없음, 목록은 3건으로 줄임)

## 1. 요약

| | A. 중앙부처 복지서비스 | B. 지자체 복지서비스 | C. 대한민국 공공서비스(혜택) 정보 |
|---|---|---|---|
| 제공 | 한국사회보장정보원 (복지로) | 한국사회보장정보원 (복지로) | 행정안전부 (정부24·보조금24) |
| 포털 | [15090532](https://www.data.go.kr/data/15090532/openapi.do) | [15108347](https://www.data.go.kr/data/15108347/openapi.do) | [15113968](https://www.data.go.kr/data/15113968/openapi.do) |
| Base URL | `https://apis.data.go.kr/B554287/NationalWelfareInformationsV001` | `https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations` | `https://api.odcloud.kr/api/gov24/v3` |
| 형식 | XML만 | XML만 [확인 P0-1b] | JSON(기본) / XML |
| 전체 건수 (2026-10-13) | 461 | 4,821 | 10,965 |
| 한 번에 받을 수 있는 건수 | 500 (1페이지로 전체) | 1,000 이상 (5페이지) | 1,000 (11페이지) |
| 개발계정 일일 트래픽 | **100건** | 1,000건 | 10,000건 |
| 승인 | 자동승인 (개발·운영) | 자동승인 (개발·운영) | 자동승인 |
| 갱신주기 | 일 1회 (활용가이드) | 실시간 (포털 표기) | 수시 (10월 수정 75건, P0-1b) |
| 변경 감지용 필드 | 없음 (등록일만) | `lastModYmd` | `수정일시` + `cond[수정일시::GTE]` 증분 조회 |
| 시니어 구조화 필드 | `lifeArray=006` (130건) | `lifeArray=006` (1,335건) | `JA0110`/`JA0111` 대상연령 |
| 장기요양 구조화 필드 | 없음 | 없음 | 없음 |

[확인] 트래픽·승인 방식·등록/수정일은 2026-10-13 기준 각 포털 페이지 표기. (A 수정일 2026-01-26, B 2026-04-13, C 2025-12-29)

**결론(제안)**: 셋 다 수집한다. A·B는 복지로 공식 원문(출처 URL 제공)이 좋고, C는 트래픽이 넉넉하고 연령이 숫자로 구조화되어 있다. 장기요양 여부는 어느 API에도 구조화 필드가 없으므로 AI 추출 대상이다.

---

## 2. A. 중앙부처 복지서비스 (`NationalWelfareInformationsV001`)

출처: 포털 Swagger(페이지 내장), 활용가이드 `활용가이드_중앙부처복지서비스(v2.2).doc` (2025-04, 서비스 버전 2.0, 시작일 2025-04-23)

### 2.1 공통 [확인]
- REST GET, 응답 XML, 인증은 `serviceKey` 쿼리 파라미터
- 활용가이드 기준 평균 응답 500ms, 최대 30 tps
- 대상: 중앙부처 사회보장급여·복지서비스 중 보건복지부 사회보장조정과가 선정한 사업
- 활용가이드에 적힌 원 서비스 URL: `https://www.bokjiro.go.kr/ssis-tbu/NationalWelfareInformationsV001/...` (data.go.kr 게이트웨이 뒤 원본). 우리는 게이트웨이(`apis.data.go.kr`)를 쓴다.

### 2.2 목록조회 `GET /NationalWelfarelistV001` [확인]

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `serviceKey` | Y | 인증키 |
| `callTp` | Y | `L` (목록) |
| `pageNo` | Y | 기본 1, 최대 1000 |
| `numOfRows` | Y | 기본 10, **최대 500** |
| `srchKeyCode` | Y | `001` 제목, `002` 내용, `003` 제목+내용 |
| `searchWrd` | N | 검색어 |
| `lifeArray` | N | 생애주기 코드 (§5.1) |
| `trgterIndvdlArray` | N | 가구유형 코드 (§5.2) |
| `intrsThemaArray` | N | 관심주제 코드 (§5.3) |
| `age` | N | 나이 |
| `onapPsbltYn` | N | 온라인 신청 가능 `Y`/`N` |
| `orderBy` | N | `date` 조회순, `popular` 인기순 (문서 표기 그대로. `date`가 "조회순"인 건 오기로 보임 [추측]) |

[확인 P0-1b] `srchKeyCode`를 빼면 `resultCode=10`(INVALID_REQUEST_PARAMETER_ERROR). 전체 목록은 `srchKeyCode=001`, 검색어 없이 호출한다.
[확인 P0-1b] `age=65`는 267건으로, `lifeArray`에 `노년`이 있는 130건 중 19건이 빠지고 생애주기가 빈 서비스 132건이 들어온다. 기준을 알 수 없어 **필터로 쓰지 않는다.**
[확인 P0-1b] 코드는 쉼표로 여러 개 넣을 수 있고 OR로 동작한다(`lifeArray=005,006` → 157건, `006`만 130건).

응답 (`<wantedList>`):

| 필드 | 설명 | 비고 |
|---|---|---|
| `totalCount`, `pageNo`, `numOfRows` | 페이징 | |
| `resultCode`, `resultMessage` | `0` / `SUCCESS` | |
| `servList` (반복) | 항목 | 샘플상 `<servList>` 요소가 항목마다 반복됨 |
| ├ `servId` | 서비스 ID (예: `WLF00001188`) | **원천 키** |
| ├ `servNm` | 서비스명 | |
| ├ `jurMnofNm` / `jurOrgNm` | 소관부처 / 소관조직 | |
| ├ `servDgst` | 서비스 요약 | |
| ├ `servDtlLink` | 복지로 상세 URL | **사용자에게 보여줄 출처 URL** |
| ├ `lifeArray` | 생애주기 **이름** 쉼표 목록 (예: `청년, 중장년, 노년`) | 코드가 아닌 한글명 |
| ├ `trgterIndvdlArray` | 가구유형 이름 목록 | |
| ├ `intrsThemaArray` | 관심주제 이름 목록 | |
| ├ `sprtCycNm` | 지원주기 (예: `1회성`, `월`, `수시`) | |
| ├ `srvPvsnNm` | 제공유형 (예: `현금지급`, `전자바우처(바우처)`) | |
| ├ `rprsCtadr` | 대표 문의처 | 아래 주의 |
| ├ `onapPsbltYn` | 온라인 신청 가능 | |
| ├ `inqNum` | 조회수 | 해시 계산에서 제외해야 함 |
| └ `svcfrstRegTs` | 서비스 등록일 (`yyyyMMdd`) | 수정일 아님 |

[확인 P0-1b] 공식 샘플의 `rprsCtadr`는 Base64처럼 보였지만, 실제 응답은 `129`, `1588-1519` 같은 평문이다.
[확인 P0-1b] `srvPvsnNm`은 `현금지급,현물지급`처럼 여러 값이 쉼표로 올 수 있다. 461건 중 165건은 `lifeArray`가 비어 있다(요소 자체가 없음).

### 2.3 상세조회 `GET /NationalWelfaredetailedV001` [확인]

파라미터: `serviceKey`(Y), `callTp=D`(Y), `servId`(Y)

응답 (`<wantedDtl>`):

| 필드 | 설명 |
|---|---|
| `servId`, `servNm`, `jurMnofNm` | 기본 정보 |
| `wlfareInfoOutlCn` | 서비스 요약 |
| `tgtrDtlCn` | **대상자 상세** (자유 텍스트, 4000자 이상 가능) |
| `slctCritCn` | **선정기준** (자유 텍스트) |
| `alwServCn` | **급여·서비스 내용** (자유 텍스트) |
| `crtrYr` | **기준연도** (예: `2025`) |
| `rprsCtadr`, `sprtCycNm`, `srvPvsnNm` | 목록과 동일 |
| `lifeArray`, `trgterIndvdlArray`, `intrsThemaArray` | 이름 목록 |
| `applmetList` (반복) | 신청방법·전달체계. `servSeCode`=070, `servSeDetailNm`(예: `신청기관연락처목록`), `servSeDetailLink`(실제로는 설명 텍스트) |
| `inqplCtadrList` (반복) | 문의처. `servSeCode`=010, 이름/연락처 |
| `inqplHmpgReldList` (반복) | 관련 사이트. `servSeCode`=020, 이름/URL |
| `basfrmList` (반복) | 서식·자료. `servSeCode`=040, 이름/다운로드 링크 |
| `baslawList` (반복) | 근거법령. `servSeCode`=030, 법령명 |
| `resultCode`, `resultMessage` | |

[확인] 샘플에서 `servSeDetailLink`는 이름과 달리 링크가 아닌 텍스트(전화번호, 설명)가 들어가기도 한다.
[확인] 샘플 `crtrYr`가 `2020`처럼 오래된 값일 수 있다 → 화면의 "기준일"은 `crtrYr`가 아니라 **우리 수집일**과 함께 보여줘야 한다.

---

## 3. B. 지자체 복지서비스 (`LocalGovernmentWelfareInformations`)

출처: 포털 Swagger(페이지 내장), `지자체복지서비스_코드표(v1.0).doc`. 별도 활용가이드는 포털에 없음.

### 3.1 목록조회 `GET /LcgvWelfarelist` [확인]

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `serviceKey` | Y | 인증키 |
| `pageNo`, `numOfRows` | N | 페이징 (최대값 문서에 없음) |
| `lifeArray` | N | 생애주기 코드 |
| `trgterIndvdlArray` | N | 가구상황 코드 |
| `intrsThemaArray` | N | 관심주제 코드 |
| `age` | N | 나이 |
| `ctpvNm` | N | 시도명 |
| `sggNm` | N | 시군구명 |
| `srchKeyCode` | N | `001` 서비스명, `002` 서비스내용, `003` 둘 다 |
| `searchWrd` | N | 검색어 |
| `arrgOrd` | N | 정렬순서 (값 문서에 없음) |

A와 다르게 `callTp`가 없다.
[확인 P0-1b] `ctpvNm=서울`과 `서울특별시` 모두 396건(부분 일치). `numOfRows=1000`이 동작한다. `age=65`는 **2건**만 나와 쓸 수 없다. `searchWrd=장기요양` 160건.
[확인 P0-1b] 시도 이름에 `전남광주통합특별시`가 있다(2026 행정구역 개편). 시도·시군구는 코드표 없이 문자열로 다룬다. 4,821건 중 730건은 `sggNm`이 없다(시도 사업).
[확인 P0-1b] 값이 없는 필드는 요소 자체가 빠진다(예: `trgterIndvdlNmArray`는 2,044건에만 있음). 목록 `lastModYmd`는 2026-07~08에 몰려 있다(일괄 갱신으로 보임).

응답 필드: `resultCode`, `resultMessage`, `numOfRows`, `pageNo`, `totalCount`, `servList`(반복):
`servId`, `servNm`, `servDgst`, `servDtlLink`, `ctpvNm`, `sggNm`, `bizChrDeptNm`(사업담당부서), `lifeNmArray`, `trgterIndvdlNmArray`, `intrsThemaNmArray`, `sprtCycNm`, `srvPvsnNm`, `aplyMtdNm`(신청방법명), `inqNum`, **`lastModYmd`(최종수정일자)**

A와 필드명이 다르다(`lifeArray` → `lifeNmArray` 등). 파서를 따로 둔다.

### 3.2 상세조회 `GET /LcgvWelfaredetailed` [확인]

파라미터: `serviceKey`(Y), `servId`(Y)

응답 필드: `servId`, `servNm`, **`enfcBgngYmd` / `enfcEndYmd`(시행 시작·종료일)**, `bizChrDeptNm`, `ctpvNm`, `sggNm`, `servDgst`, `lifeNmArray`, `trgterIndvdlNmArray`, `intrsThemaNmArray`, `sprtCycNm`, `srvPvsnNm`, `aplyMtdNm`, **`sprtTrgtCn`(지원대상)**, **`slctCritCn`(선정기준)**, **`alwServCn`(급여서비스)**, `aplyMtdCn`(신청방법 내용), `inqNum`, `lastModYmd`,
반복 목록 `inqplCtadrList` / `inqplHmpgReldList` / `baslawList` / `basfrmList` (각 `wlfareInfoDtlCd`, `wlfareInfoReldNm`, `wlfareInfoReldCn`)

[확인 P0-1b] 상세 응답은 A와 같이 `<wantedDtl>`로 감싸져 온다. `resultCode`가 맨 앞에 온다.
[확인] `enfcEndYmd`가 있어 종료된 사업을 걸러낼 수 있다(A에는 없음). [확인 P0-1b] 기한이 없으면 `99991231`이다 → DB에는 NULL로 저장한다.

---

## 4. C. 대한민국 공공서비스(혜택) 정보 (`gov24/v3`)

출처: 포털 Swagger (`https://infuser.odcloud.kr/api/stages/44436/api-docs`)

### 4.1 공통 [확인]
- 인증: 쿼리 `serviceKey=...` 또는 헤더 `Authorization: Infuser {키}` 둘 다 지원 (Swagger securityDefinitions)
- 응답 기본 JSON, `returnType=XML` 가능
- 페이징 `page`, `perPage`. 응답에 `page`, `perPage`, `totalCount`, `currentCount`, `matchCount`, `data[]`
- 필터는 `cond[필드명::연산자]=값` 형식, **필드명이 한글**
- 범위: 중앙부처 + 지자체 + 공공기관 서비스 전체 (정부24 "보조금24" 원천). [확인 P0-1b] 10,965건, 소관기관유형: 시군구 6,512 · 광역시도 1,392 · 중앙행정기관 1,052 · 공공기관 600 등. 소상공인·법인 대상(`사용자구분`)도 섞여 있어 `개인`·`가구`만 남긴다

### 4.2 엔드포인트 [확인]

| 경로 | 설명 | 조건 |
|---|---|---|
| `GET /serviceList` | 목록 | `서비스명::LIKE`, `소관기관명::LIKE`, `소관기관유형::LIKE`, `사용자구분::LIKE`, `서비스분야::LIKE`, `등록일시::LT/LTE/GT/GTE`, **`수정일시::LT/LTE/GT/GTE`** |
| `GET /serviceDetail` | 상세 | `서비스ID::EQ` |
| `GET /supportConditions` | 지원조건(구조화) | `서비스ID::EQ` |

`serviceList` 필드: 서비스ID, 지원유형, 서비스명, 서비스목적요약, 지원대상, 선정기준, 지원내용, 신청방법, 신청기한, 상세조회URL, 소관기관코드, 소관기관명, 부서명, 조회수, 소관기관유형, 사용자구분, 서비스분야, 접수기관, 전화문의, 등록일시, 수정일시

`serviceDetail` 필드: 서비스ID, 지원유형, 서비스명, 서비스목적, 신청기한, 지원대상, 선정기준, 지원내용, 신청방법, 구비서류, 접수기관명, 문의처, 온라인신청사이트URL, 수정일시, 소관기관명, 행정규칙, 자치법규, 법령, 공무원확인구비서류, 본인확인필요구비서류

[확인 P0-1b] 필터를 걸면 `totalCount`는 **여전히 전체 건수(10,965)**이고 걸러진 건수는 `matchCount`에 있다. 페이징 종료 판단은 `matchCount`로 한다.
[확인 P0-1b] `cond[수정일시::GTE]=20261001000000` → 75건. 형식은 `yyyyMMddHHmmss`. 단, `serviceDetail`의 `수정일시`는 `2026-02-03` 형식으로 다르다.
[확인 P0-1b] `supportConditions`는 조건 없이 페이지를 넘기면 전체(10,965건)를 11번 호출로 받을 수 있다.

`supportConditions` 필드 ([확인 P0-1b] 플래그 값은 `"Y"` 또는 `null`, 연령은 숫자):
- 성별 `JA0101` 남성, `JA0102` 여성
- **연령 `JA0110` 대상연령(시작), `JA0111` 대상연령(종료)** ← 시니어 필터 핵심
- 소득 `JA0201`~`JA0205` 중위소득 구간 (0~50, 51~75, 76~100, 101~200, 200% 초과)
- 생애·직업 `JA0301` 예비부모/난임, `JA0302` 임산부, `JA0303` 출산/입양, `JA0313`~`JA0316` 농·어·축산·임업인, `JA0317`~`JA0320` 학생, `JA0322` 해당없음, `JA0326` 근로자, `JA0327` 구직자, **`JA0328` 장애인**, `JA0329` 국가보훈대상자, **`JA0330` 질병/질환자**
- 가구 `JA0401` 다문화, `JA0402` 북한이탈주민, `JA0403` 한부모/조손, **`JA0404` 1인가구**, `JA0410` 해당없음, `JA0411` 다자녀, `JA0412` 무주택, `JA0413` 신규전입, `JA0414` 확대가족
- 그 외 `JA11xx`, `JA12xx`, `JA21xx`, `JA22xx`는 소상공인·법인 대상 (우리 서비스엔 불필요)

[확인 P0-1b] C의 `서비스ID`는 `135200005031`, `WII000001340`, `B55092800041`처럼 체계가 여러 가지이고 A·B의 `WLF…`와 겹치지 않는다. 중복 판별 결과는 §10.
[확인 P0-1b] 출처 URL은 `상세조회URL`(`https://www.gov.kr/portal/rcvfvrSvc/dtlEx/{서비스ID}`).
[확인 P0-1b] 목록 응답에 이미 `지원대상`, `선정기준`, `지원내용` 원문이 있다. 상세는 구비서류·법령이 필요할 때만 부른다.
[확인 P0-1b] 지역 필드가 따로 없다. `소관기관유형=시군구`이면 `소관기관명`이 `경상북도 울진군` 형식이라 여기서 지역을 뽑는다.

---

## 5. 코드표 [확인]

출처: A 활용가이드, B 코드표 문서. A와 B는 코드값이 같고 A에만 몇 개가 더 있다.

### 5.1 생애주기 `lifeArray`
`000` 구분없음(A만), `001` 영유아, `002` 아동, `003` 청소년, `004` 청년, `005` 중장년, **`006` 노년**, `007` 임신·출산

### 5.2 가구유형 / 가구상황 `trgterIndvdlArray`
`010` 다문화·탈북민, `020` 다자녀, `030` 보훈대상자, **`040` 장애인**, **`050` 저소득**, `060` 한부모·조손

### 5.3 관심주제 `intrsThemaArray`
`010` 신체건강, `020` 정신건강, `030` 생활지원, `040` 주거, `050` 일자리, `060` 문화·여가, `070` 안전·위기, `080` 임신·출산, `090` 보육, `100` 교육, `110` 입양·위탁, **`120` 보호·돌봄**, `130` 서민금융, `140` 법률, `150` 관계개선(A만), `160` 에너지(A만)

### 5.4 서비스 구분코드 (상세의 반복 목록)
`010` 문의, `020` 사이트, `030` 근거법령, `040` 서식/자료, `050` 관련FAQ(A만), `060` 인포그래픽(A만), `070` 복지사업전달체계(A만)

응답의 `lifeArray` 등은 **코드가 아니라 한글명**이 쉼표로 이어져 온다(`임신 · 출산`처럼 공백 표기도 섞임). 요청은 코드, 응답은 이름이다. 매핑 테이블을 코드에 둔다.

---

## 6. 오류 처리

### 6.1 data.go.kr 게이트웨이 (A, B) [확인]
결과코드: `0` 정상, `04` HTTP_ERROR, `10` 잘못된 파라미터, `12` 서비스 없음/폐기, `20` 접근 거부, **`22` 요청 제한 초과**, `30` 미등록 키, `31` 활용기간 만료, `99` 기타

인증·트래픽 오류는 정상 응답과 **다른 XML 구조**로 온다. 2026-10-13 실측 (임의 키로 A 목록 호출, HTTP 200):
```xml
<OpenAPI_ServiceResponse>
<cmmMsgHeader>
  <errMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</errMsg>
  <returnAuthMsg>등록되지 않은 서비스키</returnAuthMsg>
  <returnReasonCode>30</returnReasonCode>
</cmmMsgHeader>
</OpenAPI_ServiceResponse>
```
→ 파서는 루트 요소가 `OpenAPI_ServiceResponse`인지 먼저 확인한다. HTTP 상태코드만 보면 안 된다.

### 6.2 odcloud (C) [확인]
2026-10-13 실측: 미등록 키 `{"code":-4,"msg":"등록되지 않은 인증키 입니다."}`, 형식이 틀린 키 HTTP 401 `{"code":-401,"msg":"유효하지 않은 인증키 입니다."}`. 다른 코드값은 미확인.

### 6.3 정상 키인데 `12`(서비스 없음)가 날 때 [확인 P0-1b]
P0-1b에서 A·B가 `12 NO_OPENAPI_SERVICE_ERROR`, C가 HTTP 404 `not found service`를 돌려줬다. 원인은 키가 아니라 **Git Bash(MSYS)가 `/NationalWelfarelistV001` 같은 인자를 Windows 경로로 바꿔서** 요청 URL이 깨진 것이었다. 경로를 인자로 넘기는 스크립트는 `MSYS_NO_PATHCONV=1`로 실행한다. (서버 코드에는 해당 없음)

---

## 7. 시니어 혜택 필터링 설계 메모

우리 대상: 부모님(65세 이상), 장기요양 등급자, 그 보호자.

| 조건 | A 중앙 | B 지자체 | C 공공서비스 | 방법 |
|---|---|---|---|---|
| 65세 이상 | 응답 `lifeArray`에 `노년` (130/461) | `lifeNmArray`에 `노년` (1,335/4,821) | `JA0110 ≤ 나이 ≤ JA0111` (나이 미지정 포함 7,800/10,965) | 구조화 필드는 후보 추리기용. `age` 요청 파라미터는 쓰지 않는다 |
| 정확한 연령 하한(60·65·70·75세 등) | 없음 | 없음 | `JA0110` (860건이 60세 이상) | **AI 추출** (`tgtrDtlCn`/`sprtTrgtCn`/`지원대상` 텍스트) |
| 장기요양 등급 | 없음 | 없음 | 없음 | **AI 추출** + 키워드 (`장기요양` 검색: A 14, B 160, C 서비스명 50) |
| 장애 | 가구유형 `장애인` | 동일 | `JA0328` | 구조화 |
| 저소득·기초수급 | 가구유형 `저소득` | 동일 | `JA0201`~ | 구조화 + 텍스트(중위소득 %, 기초연금 수급 등) |
| 돌봄 서비스 | 관심주제 `보호·돌봄` | 동일 | `서비스분야=보호·돌봄` | 구조화 |
| 거주지 | 해당 없음(전국) | `ctpvNm`, `sggNm` | `소관기관명` 파싱 | 구조화 |
| 시행 기간 | 없음 | `enfcBgngYmd`/`enfcEndYmd` | `신청기한`(텍스트) | 구조화 + AI |

정리:
1. 전체 목록을 받고 필터는 우리 DB에서 한다. [확인 P0-1b] A 461건 중 165건, B 4,821건 중 1,466건은 생애주기가 비어 있어 `lifeArray=006`만 쓰면 놓친다(예: A의 `가사·간병 방문 지원사업`).
2. **구조화 연령을 그대로 믿으면 안 된다.** [확인 P0-1b] C의 `장기요양급여비용 본인부담금 감경`은 `JA0110=19`, `JA0111=120`이다(장기요양은 65세 미만 노인성 질환자도 대상이라서). 연령·장기요양 등급·소득 기준은 원문에서 AI가 JSON으로 추출하고, 구조화 필드는 교차 확인용으로 쓴다(ADR 0002 §6).
3. 출처 URL: A·B `servDtlLink`, C `상세조회URL`. 기준일: 수집일 + (A `crtrYr`, B `lastModYmd`, C `수정일시`).

---

## 8. 수집 전략 (파이프라인 담당 jiho용)

| | 목록 | 상세 | 변경 감지 | 하루 호출 (평시) |
|---|---|---|---|---|
| A | `numOfRows=500` 1회로 전체 | 신규 + 목록 해시가 바뀐 항목만 | 목록 필드 해시(`inqNum` 제외) | 1 + 변경분 |
| B | `numOfRows=1000` 5회 | `lastModYmd`가 바뀐 항목만 | `lastModYmd` | 5 + 변경분 |
| C | 첫 적재는 11회, 이후 `cond[수정일시::GTE]` 증분 | 목록에 원문이 있어 생략. `supportConditions`는 증분 대상만 `서비스ID::EQ` | `수정일시` | 2 + 변경분 |

**트래픽**: A는 하루 100건이다. 전체가 461건이라 첫 적재(상세 461건)는 하루에 끝낼 수 없다.
- 하루 상한을 설정값으로 둔다(A 기본 80, B 900, C 9,000). `api_daily_usage`로 센다. 응답코드 `22`를 받으면 그날 해당 원천은 중단.
- 상세 수집은 우선순위 큐로: 생애주기 `노년` 또는 이름에 `노인`/`어르신`/`장기요양`/`치매`가 들어간 항목 먼저. 첫 적재는 며칠에 나눠 진행.
- 운영 전에 "활용사례 등록" 후 트래픽 증가 신청 (사람이 할 일).

구현 메모:
- 값이 없는 XML 요소는 아예 빠진다. 반복 요소(`servList`, `applmetList` 등)는 1건일 때도 리스트로 받는다.
- 텍스트에 `&#13;`(CR)이 섞여 온다. 해시 계산 전에 줄바꿈·공백·`·` 표기를 정규화한다. `inqNum`(조회수)은 제외.
- B `enfcEndYmd=99991231`은 NULL로 저장한다. C 날짜 형식이 목록(`yyyyMMddHHmmss`)과 상세(`yyyy-MM-dd`)에서 다르다.
- 인증키는 환경변수 `PUBLIC_DATA_API_KEY`로만 주입한다. [확인 P0-1b] 64자리 16진수 키 하나로 A·B·C 모두 동작하고, 쿼리 파라미터로 넣을 때 추가 인코딩이 필요 없다. C는 `Authorization: Infuser {키}` 헤더도 동작한다.
- API 클라이언트는 인터페이스 뒤에 두고, 테스트는 `fixtures/public-api/`의 실제 응답을 쓴다.

---

## 9. 인증키 발급 방법

1. [data.go.kr](https://www.data.go.kr) 회원가입·로그인 [확인]
2. 각 API 페이지(§1 링크)에서 **활용신청** → 활용 목적 입력. 세 API 모두 **자동승인** [확인]
3. 마이페이지 → 데이터활용 → Open API → 활용신청 현황에서 **일반 인증키** 확인 [추측: 메뉴명은 바뀌었을 수 있음]
4. [확인 P0-1b] 이번에 받은 키는 64자리 16진수라 Encoding/Decoding 구분 없이 그대로 쓰면 된다.
5. 승인 직후엔 키가 게이트웨이에 반영될 때까지 시간이 걸린다. 이번에는 약 1~2시간 걸렸다 [확인 P0-1b, 사용자 보고]
6. 개발계정 활용기간은 기한이 있다(만료 시 `31` 오류). 기간 연장 신청 필요 [확인: 오류코드 존재 / 기간 길이는 미확인]
7. 운영계정: 서비스 공개 후 활용사례 등록 → 트래픽 증가 신청 [확인: 포털 표기]

---

## 10. 실호출 검증 결과 (P0-1b, 2026-10-13)

호출 수: A 10건(이 중 2건은 위 6.3 문제로 게이트웨이에서 거절), B 약 15건, C 약 30건.

- [x] A 전체 461건, `numOfRows=500` 한 번으로 전부 받음. `lifeArray=006` 130건.
- [x] A `srchKeyCode` 없이 호출하면 `resultCode=10`. `001` + 검색어 없음 → 전체.
- [x] A `age=65`는 기준을 알 수 없는 결과(267건, 노년 19건 누락). 코드 복수 지정은 OR.
- [x] A `rprsCtadr`는 평문.
- [x] B 전체 4,821건, `numOfRows=1000` 동작, 상세 루트 `wantedDtl`, `ctpvNm`은 부분 일치(`서울` = `서울특별시`). `age=65`는 2건뿐이라 사용 불가.
- [x] B와 A의 `servId`는 겹치지 않는다(0건). 원천별 유일 키 `(source, source_id)`로 충분.
- [x] C 전체 10,965건. `supportConditions` 플래그는 `"Y"`/`null`, 연령은 숫자. `JA0110`은 9,953건(91%)에 있음. 상한은 `120`이 "제한 없음"으로 쓰인다.
- [x] 중복: 정규화한 서비스명 완전 일치 기준 **A 461건 중 194건(42%)**, B 4,821건 중 1,053건이 C에 같은 이름이 있다. 그러나 B는 이름이 같은 다른 지역 사업이 많다(B 안에서도 같은 이름 363쌍, 예: `효행장려지원` 양주시 ↔ C의 울진군). → **이름만으로 자동 병합하면 안 된다**는 ADR 0002 §3 결정을 뒷받침한다. 지역(B `ctpvNm`/`sggNm` ↔ C `소관기관명`)이 같을 때만 자동 병합.
- [x] 장기요양 검색: A `searchWrd=장기요양` 14건(장애인활동지원 등 관련 없는 것도 섞임), B 160건, C 서비스명 LIKE 50건 / 전체 원문 기준 148건.

스키마 영향: V1으로 충분하다. 변경 없음. (B `99991231` → NULL, C 지역 파싱은 수집 코드에서 처리)

## 11. 범위 밖 (참고)

- 국민건강보험공단 장기요양기관 검색 API: 시설 정보이지 혜택 정보가 아니라 이번 범위에서 제외. Phase 2 이후 "주변 요양기관" 기능 후보. [추측: 상세 미조사]
- 복지로 화면 크롤링: API로 충분하므로 하지 않는다.
