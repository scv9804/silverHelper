export type AccountValue<T> = { accountId: number; value: T };

export function valueForAccount<T>(state: AccountValue<T> | null, accountId: number): T | null {
  return state?.accountId === accountId ? state.value : null;
}

export function clearForAccount<T>(state: AccountValue<T> | null, accountId: number): AccountValue<T> | null {
  return state?.accountId === accountId ? null : state;
}

export function settleForAccount<T>(state: AccountValue<T> | null, accountId: number, value: T): AccountValue<T> {
  return state && state.accountId !== accountId ? state : { accountId, value };
}
