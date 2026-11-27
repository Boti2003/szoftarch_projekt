// Small presentation helpers shared across the social pages.

// Friend requests and duels reference the counterpart only by UUID (the social
// service stores no usernames). Until a display name is resolved, show a short,
// stable label derived from the id so the UI is readable.
export function shortId(id: string): string {
  return id.slice(0, 8);
}

// A human label for a user when only an id (and maybe a display name) is known.
export function userLabel(id: string, displayName?: string | null): string {
  return displayName && displayName.trim().length > 0
    ? displayName
    : `Felhasználó ${shortId(id)}`;
}
