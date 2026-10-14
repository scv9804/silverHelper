import { test } from "node:test";
import assert from "node:assert/strict";

const values = new Map();
globalThis.localStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, value),
  removeItem: (key) => values.delete(key),
};

const { initialProfile, useProfile } = await import("../src/store.ts");

test("로그아웃 초기화는 부모님 민감 정보와 편집 대상을 메모리·저장소에서 제거한다", () => {
  useProfile.getState().update({
    age: 79,
    ctpvName: "서울특별시",
    incomeMedianPct: 45,
    basicLivelihood: true,
    disability: true,
    veteran: true,
  });
  useProfile.getState().startEditing({ id: 7, userId: 1, name: "부모님" });
  assert.match(values.get("silverhelper-profile"), /incomeMedianPct/);

  useProfile.getState().reset();

  assert.deepEqual(useProfile.getState().profile, initialProfile);
  assert.equal(useProfile.getState().editing, null);
  assert.equal(values.has("silverhelper-profile"), false);
});
