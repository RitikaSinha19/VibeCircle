export interface LocalProfilePreferences {
  city: string;
  interests: string[];
  completedAt: string;
}

const STORAGE_PREFIX = "vibecircle_profile_preferences_";

function getStorageKey(userId: string | number): string {
  return `${STORAGE_PREFIX}${String(userId)}`;
}

export const profilePreferences = {
  get(userId: string | number): LocalProfilePreferences | null {
    try {
      const raw = localStorage.getItem(getStorageKey(userId));
      if (!raw) return null;

      const parsed = JSON.parse(raw) as LocalProfilePreferences;
      if (
        typeof parsed.city !== "string" ||
        !Array.isArray(parsed.interests) ||
        typeof parsed.completedAt !== "string"
      ) {
        return null;
      }
      return parsed;
    } catch {
      return null;
    }
  },

  save(userId: string | number, preferences: Omit<LocalProfilePreferences, "completedAt">): void {
    const value: LocalProfilePreferences = {
      ...preferences,
      completedAt: new Date().toISOString(),
    };
    localStorage.setItem(getStorageKey(userId), JSON.stringify(value));
  },

  isComplete(userId: string | number): boolean {
    const preferences = this.get(userId);
    return Boolean(
      preferences?.city.trim() && preferences.interests.length >= 3
    );
  },
};
