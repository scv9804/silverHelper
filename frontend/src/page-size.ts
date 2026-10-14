import { useState } from "react";

export const pageSizes = [10, 20, 50, 100] as const;
const key = "silverhelper-page-size";
export function readPageSize() {
  try { const saved = Number(sessionStorage.getItem(key)); return pageSizes.includes(saved as typeof pageSizes[number]) ? saved : 20; }
  catch { return 20; }
}
export function storePageSize(next: number) {
  if (!pageSizes.includes(next as typeof pageSizes[number])) return false;
  try { sessionStorage.setItem(key, String(next)); } catch { /* storage unavailable */ }
  return true;
}
export function usePageSize() {
  const [size, setSize] = useState<number>(readPageSize);
  const changeSize = (next: number) => {
    if (!storePageSize(next)) return;
    setSize(next);
  };
  return { size, changeSize };
}
