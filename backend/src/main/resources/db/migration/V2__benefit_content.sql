-- V2: 혜택 상세 화면용 원문 필드 (openapi.yaml BenefitDetail)
-- 원문은 benefit_raw.payload에도 있지만, 조회 API가 원천별 JSON 구조를 몰라도 되게 정규화해서 둔다.
ALTER TABLE benefit
    ADD COLUMN target_content   text,
    ADD COLUMN criteria_content text,
    ADD COLUMN benefit_content  text,
    ADD COLUMN apply_content    text;
