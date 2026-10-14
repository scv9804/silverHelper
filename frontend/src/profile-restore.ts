import { api, type Me, type SavedProfile } from "./api.ts";
import { lastProfileId, useProfile } from "./store.ts";

export async function restoreSelectedProfile(account: Me, isCurrent: () => boolean) {
  if (!isCurrent()) return;
  const state = useProfile.getState();
  state.setGuest(false);
  if (state.hasInput) return;
  const id = lastProfileId(account.id);
  if (id === null) return;
  try {
    const profiles = await api<SavedProfile[]>("/api/me/profiles");
    if (!isCurrent() || useProfile.getState().hasInput) return;
    const saved = profiles.find((item) => item.id === id);
    if (!saved) return;
    const { id: _id, name: _name, ...fields } = saved;
    void _id; void _name;
    useProfile.getState().loadProfile(fields);
  } catch { /* Profile list remains available on My Page. */ }
}
