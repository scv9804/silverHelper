# 공공 복지서비스 API 조사 (Phase 0-1)

- 작성: doyun, 2026-10-13
- 목적: 수집 파이프라인(`pipeline`)과 DB 스키마(0-2)의 근거 자료
- 표기
  - **[확인]** 공식 자료(포털 페이지, Swagger, 제공기관 활용가이드)나 실제 호출로 확인한 사실. 출처를 함께 적는다.
  - **[추측]** 문서에 없거나 모호해서 추정한 내용. 인증키 발급 후 실제 호출로 검증해야 한다.
- 아직 인증키가 없어 **정상 응답은 직접 받아보지 못했다.** 응답 예시는 모두 공식 문서 샘플이다. 인증키 없이 호출한 오류 응답만 실측했다.

## 1. 요약

| | A. 중앙부처 복지서비스 | B. 지자체 복지서비스 | C. 대한민국 공공서비스(혜택) 정보 |
|---|---|---|---|
| 제공 | 한국사회보장정보원 (복지로) | 한국사회보장정보원 (복지로) | 행정안전부 (정부24·보조금24) |
| 포털 | [15090532](https://www.data.go.kr/data/15090532/openapi.do) | [15108347](https://www.data.go.kr/data/15108347/openapi.do) | [15113968](https://www.data.go.kr/data/15113968/openapi.do) |
| Base URL | `https://apis.data.go.kr/B554287/NationalWelfareInformationsV001` | `https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations` | `https://api.odcloud.kr/api/gov24/v3` |
| 형식 | XML만 | XML만 [추측: Swagger상 XML] | JSON(기본) / XML |
| 개발계정 일일 트래픽 | **100건** | 1,000건 | 10,000건 |
| 승인 | 자동승인 (개발·운영) | 자동승인 (개발·운영) | 자동승인 |
| 갱신주기 | 일 1회 (활용가이드) | 실시간 (포털 표기) | [추측] 수시 |
| 변경 감지용 필드 | 없음 (등록일만) | `lastModYmd` | `수정일시` + 조회 조건 필터 |
| 시니어 구조화 필드 | `lifeArray=006`, `age` | `lifeArray=006`, `age` | `JA0110`/`JA0111` 대상연령 |
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

[추측] `srchKeyCode`가 필수라서 전체 목록을 받을 때도 `001`을 넣고 `searchWrd`를 비워야 할 것으로 보인다.
[추측] `age`는 "그 나이가 대상에 포함되는 서비스"로 거르는 것 같지만 기준이 문서에 없다. 실호출로 확인 필요.
[추측] `lifeArray` 등에 여러 코드를 쉼표로 넣을 수 있는지 불명.

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

주의 [확인]: 공식 샘플 응답의 `rprsCtadr` 값이 `e1c2kzjZq/xMywMyqUnhotU=` 같은 Base64 형태다. 암호화/인코딩되어 내려올 가능성이 있다. [추측] 실제 응답도 그렇다면 문의처는 상세조회의 `inqplCtadrList`를 써야 한다.

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

A와 다르게 `callTp`가 없다. [추측] `ctpvNm`은 `서울특별시` 같은 정식 명칭이어야 할 것. `numOfRows` 최대는 A처럼 500일 가능성.

응답 필드: `resultCode`, `resultMessage`, `numOfRows`, `pageNo`, `totalCount`, `servList`(반복):
`servId`, `servNm`, `servDgst`, `servDtlLink`, `ctpvNm`, `sggNm`, `bizChrDeptNm`(사업담당부서), `lifeNmArray`, `trgterIndvdlNmArray`, `intrsThemaNmArray`, `sprtCycNm`, `srvPvsnNm`, `aplyMtdNm`(신청방법명), `inqNum`, **`lastModYmd`(최종수정일자)**

A와 필드명이 다르다(`lifeArray` → `lifeNmArray` 등). 파서를 따로 둔다.

### 3.2 상세조회 `GET /LcgvWelfaredetailed` [확인]

파라미터: `serviceKey`(Y), `servId`(Y)

응답 필드: `servId`, `servNm`, **`enfcBgngYmd` / `enfcEndYmd`(시행 시작·종료일)**, `bizChrDeptNm`, `ctpvNm`, `sggNm`, `servDgst`, `lifeNmArray`, `trgterIndvdlNmArray`, `intrsThemaNmArray`, `sprtCycNm`, `srvPvsnNm`, `aplyMtdNm`, **`sprtTrgtCn`(지원대상)**, **`slctCritCn`(선정기준)**, **`alwServCn`(급여서비스)**, `aplyMtdCn`(신청방법 내용), `inqNum`, `lastModYmd`,
반복 목록 `inqplCtadrList` / `inqplHmpgReldList` / `baslawList` / `basfrmList` (각 `wlfareInfoDtlCd`, `wlfareInfoReldNm`, `wlfareInfoReldCn`)

[추측] Swagger상 루트 래퍼 요소 이름이 없다. A처럼 `wantedDtl`로 감싸져 올 수 있음. 실호출로 확인.
[확인] `enfcEndYmd`가 있어 종료된 사업을 걸러낼 수 있다(A에는 없음).

---

## 4. C. 대한민국 공공서비스(혜택) 정보 (`gov24/v3`)

출처: 포털 Swagger (`https://infuser.odcloud.kr/api/stages/44436/api-docs`)

### 4.1 공통 [확인]
- 인증: 쿼리 `serviceKey=...` 또는 헤더 `Authorization: Infuser {키}` 둘 다 지원 (Swagger securityDefinitions)
- 응답 기본 JSON, `returnType=XML` 가능
- 페이징 `page`, `perPage`. 응답에 `page`, `perPage`, `totalCount`, `currentCount`, `matchCount`, `data[]`
- 필터는 `cond[필드명::연산자]=값` 형식, **필드명이 한글**
- 범위: 중앙부처 + 지자체 + 공공기관 서비스 전체 (정부24 "보조금24" 원천) [추측: 포털 설명 기준, 건수 미확인]

### 4.2 엔드포인트 [확인]

| 경로 | 설명 | 조건 |
|---|---|---|
| `GET /serviceList` | 목록 | `서비스명::LIKE`, `소관기관명::LIKE`, `소관기관유형::LIKE`, `사용자구분::LIKE`, `서비스분야::LIKE`, `등록일시::LT/LTE/GT/GTE`, **`수정일시::LT/LTE/GT/GTE`** |
| `GET /serviceDetail` | 상세 | `서비스ID::EQ` |
| `GET /supportConditions` | 지원조건(구조화) | `서비스ID::EQ` |

`serviceList` 필드: 서비스ID, 지원유형, 서비스명, 서비스목적요약, 지원대상, 선정기준, 지원내용, 신청방법, 신청기한, 상세조회URL, 소관기관코드, 소관기관명, 부서명, 조회수, 소관기관유형, 사용자구분, 서비스분야, 접수기관, 전화문의, 등록일시, 수정일시

`serviceDetail` 필드: 서비스ID, 지원유형, 서비스명, 서비스목적, 신청기한, 지원대상, 선정기준, 지원내용, 신청방법, 구비서류, 접수기관명, 문의처, 온라인신청사이트URL, 수정일시, 소관기관명, 행정규칙, 자치법규, 법령, 공무원확인구비서류, 본인확인필요구비서류

`supportConditions` 필드 (값 의미는 [추측]: 해당하면 `Y`, 아니면 null):
- 성별 `JA0101` 남성, `JA0102` 여성
- **연령 `JA0110` 대상연령(시작), `JA0111` 대상연령(종료)** ← 시니어 필터 핵심
- 소득 `JA0201`~`JA0205` 중위소득 구간 (0~50, 51~75, 76~100, 101~200, 200% 초과)
- 생애·직업 `JA0301` 예비부모/난임, `JA0302` 임산부, `JA0303` 출산/입양, `JA0313`~`JA0316` 농·어·축산·임업인, `JA0317`~`JA0320` 학생, `JA0322` 해당없음, `JA0326` 근로자, `JA0327` 구직자, **`JA0328` 장애인**, `JA0329` 국가보훈대상자, **`JA0330` 질병/질환자**
- 가구 `JA0401` 다문화, `JA0402` 북한이탈주민, `JA0403` 한부모/조손, **`JA0404` 1인가구**, `JA0410` 해당없음, `JA0411` 다자녀, `JA0412` 무주택, `JA0413` 신규전입, `JA0414` 확대가족
- 그 외 `JA11xx`, `JA12xx`, `JA21xx`, `JA22xx`는 소상공인·법인 대상 (우리 서비스엔 불필요)

[추측] C의 `서비스ID`와 A·B의 `servId`(WLF…)는 체계가 다르다. 같은 사업이 A/B와 C에 중복으로 있을 것이므로 중복 판별이 필요하다(§7).
[추측] C는 출처 URL로 `상세조회URL`(정부24)을 쓴다.

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
2026-10-13 실측 (임의 키): `{"code":-4,"msg":"등록되지 않은 인증키 입니다."}`. 다른 코드값 목록은 [추측] 미확인.

---

## 7. 시니어 혜택 필터링 설계 메모

우리 대상: 부모님(65세 이상), 장기요양 등급자, 그 보호자.

| 조건 | A 중앙 | B 지자체 | C 공공서비스 | 방법 |
|---|---|---|---|---|
| 65세 이상 | `lifeArray=006`, `age=65` 요청 필터 / 응답 `lifeArray`에 `노년` | 동일 (`lifeNmArray`) | `JA0110 ≤ 나이 ≤ JA0111` | 구조화 필드 우선, 텍스트로 보강 |
| 정확한 연령 하한(60·65·70·75세 등) | 없음 | 없음 | `JA0110` | **AI 추출** (`tgtrDtlCn`/`sprtTrgtCn`/`선정기준` 텍스트) |
| 장기요양 등급 | 없음 | 없음 | 없음 (`JA0330` 질병/질환자가 가장 가까움) | **AI 추출** + 키워드 검색 `장기요양`, `요양등급`, `치매` |
| 장애 | 가구유형 `장애인` | 동일 | `JA0328` | 구조화 |
| 저소득·기초수급 | 가구유형 `저소득` | 동일 | `JA0201`~ | 구조화 + 텍스트(중위소득 %, 기초연금 수급 등) |
| 돌봄 서비스 | 관심주제 `보호·돌봄` | 동일 | `서비스분야` | 구조화 |
| 거주지 | 해당 없음(전국) | `ctpvNm`, `sggNm` | `소관기관명` | 구조화 |
| 시행 기간 | 없음 | `enfcBgngYmd`/`enfcEndYmd` | `신청기한`(텍스트) | 구조화 + AI |

정리:
1. 1차 수집 범위는 넓게 잡는다. `lifeArray=006`만 쓰면 생애주기 태깅이 빠진 시니어 서비스(예: `000` 구분없음, 장기요양 가족 지원)를 놓친다. [추측] → 전체 목록을 받고 필터는 우리 DB에서 한다.
2. 연령·장기요양 등급·소득 기준은 원문 텍스트에서 AI가 JSON으로 추출한다(CLAUDE.md 파이프라인 원칙). 구조화 필드는 AI 결과를 검증하는 기준값으로도 쓴다(예: AI가 "65세 이상"인데 `lifeArray`에 `노년`이 없으면 검토 대기열).
3. 출처 URL: A·B `servDtlLink`, C `상세조회URL`. 기준일: 수집일 + (A `crtrYr`, B/C 최종수정일).

---

## 8. 수집 전략 (파이프라인 담당 jiho용 초안)

| | 목록 | 상세 | 변경 감지 |
|---|---|---|---|
| A | `numOfRows=500`로 전체 페이지 순회 (일 1회) | 신규 `servId` + 목록 해시가 바뀐 항목만 | 목록에 수정일 없음 → 목록 필드 해시(`inqNum` 제외). [추측] 목록은 그대로인데 상세만 바뀌는 경우를 놓칠 수 있어 주 1회 전체 상세 재수집 |
| B | 전체 순회 | `lastModYmd`가 바뀐 항목만 | `lastModYmd` |
| C | `cond[수정일시::GTE]=마지막 수집 시각` 증분 | 변경분의 `serviceDetail` + `supportConditions` | `수정일시` |

**트래픽 위험**: A 개발계정은 하루 100건이다. 전체 건수는 [추측] 수백 건 규모라 첫 전체 상세 수집에 며칠이 걸린다. 해결책:
- 운영 단계 전환 시 "활용사례 등록" 후 트래픽 증가 신청 (포털 안내)
- 첫 적재는 며칠에 나눠 실행하고, 이후엔 변경분만
- 호출 수를 세서 상한(예: 90건) 넘으면 다음 날로 미룬다. 응답코드 `22`를 받으면 즉시 중단

구현 메모:
- XML 반복 요소(`servList`, `applmetList` 등)는 1건일 때도 리스트로 받도록 파서를 설정한다.
- 원문 해시는 사용자 노출 필드만 정규화(공백·`·` 표기 통일)해서 계산. `inqNum`(조회수)은 제외.
- 인증키는 환경변수(`PUBLIC_DATA_API_KEY`)로만 주입. A·B·C는 같은 포털 계정 키 하나로 쓸 수 있다. [추측: 포털 계정당 일반 인증키가 하나로 발급되는 것이 일반적]
- API 클라이언트는 인터페이스 뒤에 두고, 테스트에서는 이 문서의 샘플 XML/JSON을 픽스처로 쓴다.

---

## 9. 인증키 발급 방법

1. [data.go.kr](https://www.data.go.kr) 회원가입·로그인 [확인]
2. 각 API 페이지(§1 링크)에서 **활용신청** → 활용 목적 입력. 세 API 모두 **자동승인** [확인]
3. 마이페이지 → 데이터활용 → Open API → 활용신청 현황에서 **일반 인증키** 확인 [추측: 포털 일반 절차. 메뉴명은 바뀌었을 수 있음]
4. 키는 Encoding/Decoding 두 형태로 제공된다. HTTP 클라이언트가 URL 인코딩을 해주면 **Decoding 키**를 쓴다(이중 인코딩되면 `30` 미등록 키 오류) [추측: 포털 일반 안내, 흔한 오류]
5. 승인 직후엔 키가 게이트웨이에 반영될 때까지 시간이 걸릴 수 있다(보통 1시간 안팎이라는 이용자 보고가 많음) [추측]
6. 개발계정 활용기간은 기한이 있다(만료 시 `31` 오류). 기간 연장 신청 필요 [확인: 오류코드 존재 / 기간 길이는 미확인]
7. 운영계정: 서비스 공개 후 활용사례 등록 → 트래픽 증가 신청 [확인: 포털 표기]

→ **사람(사용자)이 직접 해야 할 일**: 포털 가입과 3개 API 활용신청. 받은 키를 `.env`의 `PUBLIC_DATA_API_KEY`에 넣어주면 §10 항목을 실호출로 검증한다.

---

## 10. 실호출로 검증할 항목 (키 발급 후)

- [ ] A: 전체 `totalCount` (노년 `lifeArray=006` 건수도)
- [ ] A: `srchKeyCode`만 넣고 검색어 없이 전체 목록이 오는지
- [ ] A: `age=65`의 필터 의미, 코드 복수 지정 가능 여부
- [ ] A: `rprsCtadr` 실제 값이 암호화돼 있는지
- [ ] B: 전체 건수, `numOfRows` 최대값, 상세 응답 루트 요소 이름, `ctpvNm` 값 형식
- [ ] B: A와 `servId`가 겹치는지 (둘 다 WLF 접두사)
- [ ] C: 전체 건수, `supportConditions` 값 형식(`Y`/null?), `JA0110`이 비어 있는 비율
- [ ] C와 A·B 간 같은 사업 중복 정도 (서비스명 기준)
- [ ] 장기요양 키워드 검색 결과 건수 (A·B `searchWrd=장기요양`, C `cond[서비스명::LIKE]=장기요양`)

## 11. 범위 밖 (참고)

- 국민건강보험공단 장기요양기관 검색 API: 시설 정보이지 혜택 정보가 아니라 이번 범위에서 제외. Phase 2 이후 "주변 요양기관" 기능 후보. [추측: 상세 미조사]
- 복지로 화면 크롤링: API로 충분하므로 하지 않는다.
