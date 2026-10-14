import { test } from "node:test";
import assert from "node:assert/strict";

const values = new Map([["silverhelper-profile", JSON.stringify({ incomeMedianPct: 45, disability: true })]]);
globalThis.localStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, value),
  removeItem: (key) => values.delete(key),
};
const sessionValues = new Map();
globalThis.sessionStorage = {
  getItem: (key) => sessionValues.get(key) ?? null,
  setItem: (key, value) => sessionValues.set(key, value),
  removeItem: (key) => sessionValues.delete(key),
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
  assert.equal(sessionValues.has("silverhelper-guest-profile"), false);
}

test("초기 refresh 실패 뒤 비로그인 입력은 탭 세션에 남고 로그아웃 시 지워진다", async () => {
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
  assert.equal(JSON.parse(sessionValues.get("silverhelper-guest-profile")).incomeMedianPct, 45);
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

test("로그인 직후 비로그인 입력은 유지하고 편집 대상만 지운다", () => {
  useProfile.getState().reset();
  sensitiveProfile();
  beginClientSession("account-b-token");
  assert.equal(useProfile.getState().profile.incomeMedianPct, 45);
  assert.equal(useProfile.getState().editing, null);
  assert.equal(useProfile.getState().guest, false);
  assert.ok(values.get(SESSION_CHANGE_KEY).startsWith("login:"));
  clearClientSession();
  assertCleared();
});

test("서버 프로필 마지막 선택은 계정 ID에 묶이고 로그아웃 때 지워진다", async () => {
  const { rememberProfile, lastProfileId } = await import("../src/store.ts");
  rememberProfile(1, 42);
  assert.equal(lastProfileId(1), 42);
  assert.equal(lastProfileId(2), null);
  clearClientSession();
  assert.equal(lastProfileId(1), null);
});

test("다른 탭 로그아웃은 이전 입력을 지운다", () => {
  sensitiveProfile();
  clearClientSession();
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

test("목록 표시 개수는 허용 값만 탭 세션에 보관한다", async () => {
  const { readPageSize, storePageSize } = await import("../src/page-size.ts");
  sessionValues.clear();
  assert.equal(readPageSize(), 20);
  assert.equal(storePageSize(100), true);
  assert.equal(readPageSize(), 100);
  assert.equal(storePageSize(101), false);
  assert.equal(readPageSize(), 100);
});
