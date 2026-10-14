# 발송 결과 불확실 건 확인

메일 전송을 시도한 뒤 SMTP 응답이나 `SENT` 상태 저장에 실패하면 배송 행은 `PENDING`으로 남는다. 배치는 `PENDING`을 자동 재발송하지 않는다. SMTP 수신 여부를 정확히 한 번으로 보장할 수 없기 때문이다.

1. `notification_delivery`에서 오래된 `PENDING` 행의 `id`, `user_id`, `profile_id`, `benefit_id`, `kind`, `event_key`, `created_at`, `error_message`를 확인한다. 수신 주소는 `user_account`에서 확인한다.
2. 해당 시각과 수신 주소, 혜택 이름으로 SMTP 공급자 또는 Mailpit 발송 기록을 대조한다.
3. 전달이 확인되면 해당 행만 `status='SENT', sent_at=<확인한 발송 시각>, error_message=null`로 보정한다.
4. 전달되지 않았음이 확인된 경우에만 해당 행을 `status='FAILED'`로 바꾼다. 다음 배치가 `FAILED`를 예약해 재시도한다.
5. 전달 여부가 불명확하면 `PENDING`을 유지하고 공급자 기록을 추가 확인한다. 수동 재발송도 중복 가능성을 검토한 뒤 결정한다.
