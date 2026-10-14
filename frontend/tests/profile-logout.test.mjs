import { test } from "node:test";
import assert from "node:assert/strict";

const values = new Map([["silverhelper-profile", JSON.stringify({ incomeMedianPct: 45, disability: true })]]);
globalThis.localStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, value),
  removeItem: (key) => values.delete(key),
};
globalThis.window = new EventTarget();

const { initialProfile, useProfile } = await import("../src/store.ts");
const { api, refreshToken, setAccessToken } = await import("../src/api.ts");
const { beginClientSession, clearClientSession, SESSION_CHANGE_KEY, subscribeSessionChanges } =
  await import("../src/session.ts");

function sensitiveProfile() {
  useProfile.getState().update({ incomeMedianPct: 45, basicLivelihood: true, disability: true, veteran: true });
  useProfile.getState().startEditing({ id: 7, userId: 1, name: "부모님" });
}

function assertCleared() {
  assert.deepEqual(useProfile.getState().profile, initialProfile);
  assert.equal(useProfile.getState().editing, null);
  assert.equal(values.has("silverhelper-profile"), false);
}

test("초기 refresh가 실패해도 과거 저장 프로필은 복원되지 않고 수정은 메모리에만 둔다", async () => {
  const oldFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(
    JSON.stringify({ success: false, data: null, error: { code: "UNAUTHORIZED", message: "만료" } }),
    { status: 401 },
  );
  try { await assert.rejects(refreshToken(), /만료/); }
  finally { globalThis.fetch = oldFetch; }
  assertCleared();
  sensitiveProfile();
  assert.equal(useProfile.getState().profile.incomeMedianPct, 45);
  assert.equal(values.has("silverhelper-profile"), false);
  clearClientSession();
  assertCleared();
});

test("늦게 끝난 이전 계정 refresh가 새 로그인 토큰을 덮어쓰지 않는다", async () => {
  const oldFetch = globalThis.fetch;
  let finishRefresh;
  globalThis.fetch = () => new Promise((resolve) => { finishRefresh = resolve; });
  try {
    const pending = refreshToken();
    beginClientSession("account-b-token");
    finishRefresh(new Response(JSON.stringify({
      success: true, data: { accessToken: "account-a-token", user: { id: 1 } }, error: null,
    }), { status: 200 }));
    await assert.rejects(pending, /Session changed/);
    let headers;
    globalThis.fetch = async (_path, options) => {
      headers = options.headers;
      return new Response(JSON.stringify({ success: true, data: { ok: true }, error: null }), { status: 200 });
    };
    await api("/api/auth/exchange");
    assert.equal(headers.Authorization, "Bearer account-b-token");
  } finally {
    globalThis.fetch = oldFetch;
  }
});

test("다른 계정 로그인은 이전 프로필과 편집 대상을 지우고 다른 탭에 알린다", () => {
  sensitiveProfile();
  beginClientSession("account-b-token");
  assertCleared();
  assert.ok(values.get(SESSION_CHANGE_KEY));
});

test("다른 탭의 로그아웃 신호가 현재 탭의 프로필과 액세스 토큰을 지운다", async () => {
  sensitiveProfile();
  setAccessToken("account-a-token");
  const unsubscribe = subscribeSessionChanges(clearClientSession);
  const event = new Event("storage");
  Object.assign(event, { key: SESSION_CHANGE_KEY, newValue: "another-tab-logout" });
  window.dispatchEvent(event);
  assertCleared();

  const oldFetch = globalThis.fetch;
  let headers;
  globalThis.fetch = async (_path, options) => {
    headers = options.headers;
    return new Response(JSON.stringify({ success: true, data: { ok: true }, error: null }), { status: 200 });
  };
  try {
    await api("/api/auth/exchange");
    assert.equal(headers.Authorization, undefined);
  } finally {
    globalThis.fetch = oldFetch;
    unsubscribe();
  }
});

test("세션 만료 뒤 refresh가 401이면 열린 화면의 민감 정보가 지워진다", async () => {
  sensitiveProfile();
  setAccessToken("expired-token");
  const unsubscribe = subscribeSessionChanges(clearClientSession);
  const oldFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(
    JSON.stringify({ success: false, data: null, error: { code: "UNAUTHORIZED", message: "만료" } }),
    { status: 401 },
  );
  try {
    await assert.rejects(api("/api/me/profiles"), /만료/);
    assertCleared();
  } finally {
    globalThis.fetch = oldFetch;
    unsubscribe();
  }
});
