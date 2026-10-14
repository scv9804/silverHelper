import { test } from "node:test";
import assert from "node:assert/strict";

const localValues = new Map();
globalThis.localStorage = {
  getItem: (key) => localValues.get(key) ?? null,
  setItem: (key, value) => localValues.set(key, value),
  removeItem: (key) => localValues.delete(key),
};
globalThis.sessionStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };

const { initialProfile, rememberProfile, useProfile } = await import("../src/store.ts");
const { restoreSelectedProfile } = await import("../src/profile-restore.ts");
const { valueForAccount, clearForAccount, settleForAccount } = await import("../src/account-draft.ts");

const response = (id, ctpvName) => new Response(JSON.stringify({
  success: true,
  data: [{ ...initialProfile, id, name: "부모님", age: 75, ctpvName }],
  error: null,
}), { status: 200 });

for (const order of ["B 먼저", "A 먼저"]) {
  test(`프로필 복원 경합: ${order} 응답이어도 B 화면에 A 프로필이 들어오지 않는다`, async () => {
    useProfile.getState().reset();
    const oldFetch = globalThis.fetch;
    const requests = [];
    globalThis.fetch = () => new Promise((resolve) => { requests.push(resolve); });
    let generation = 1;
    let accountId = 1;
    try {
      rememberProfile(1, 101);
      const a = restoreSelectedProfile({ id: 1 }, () => generation === 1 && accountId === 1);
      assert.equal(requests.length, 1);

      // 다른 탭의 B 로그인으로 현재 계정과 세대가 바뀐다.
      useProfile.getState().reset();
      generation = 2;
      accountId = 2;
      rememberProfile(2, 202);
      const b = restoreSelectedProfile({ id: 2 }, () => generation === 2 && accountId === 2);
      assert.equal(requests.length, 2);

      if (order === "B 먼저") {
        requests[1](response(202, "부산광역시"));
        await b;
        requests[0](response(101, "서울특별시"));
        await a;
      } else {
        requests[0](response(101, "서울특별시"));
        await a;
        assert.equal(useProfile.getState().profile.ctpvName, "");
        requests[1](response(202, "부산광역시"));
        await b;
      }
      assert.equal(useProfile.getState().profile.ctpvName, "부산광역시");
    } finally { globalThis.fetch = oldFetch; useProfile.getState().reset(); }
  });
}

test("알림 초안과 메시지는 계정 전환 즉시 숨고 늦은 A 저장 응답이 B 초안을 지우지 않는다", () => {
  const aSettings = { emailEnabled: true, newBenefitEnabled: true, deadlineEnabled: true, deadlineDays: 3 };
  const bSettings = { emailEnabled: false, newBenefitEnabled: false, deadlineEnabled: false, deadlineDays: 10 };
  let draft = { accountId: 1, value: aSettings };
  let message = { accountId: 1, value: "A의 저장 결과" };

  assert.equal(valueForAccount(draft, 2), null);
  assert.equal(valueForAccount(message, 2), null);
  draft = { accountId: 2, value: bSettings };
  message = { accountId: 2, value: "B의 입력 중" };

  // A의 저장 요청이 B의 편집 시작 후 완료된다.
  draft = clearForAccount(draft, 1);
  message = settleForAccount(message, 1, "A 저장 완료");
  assert.deepEqual(valueForAccount(draft, 2), bSettings);
  assert.equal(valueForAccount(message, 2), "B의 입력 중");
  assert.equal(valueForAccount(draft, 1), null);
});
