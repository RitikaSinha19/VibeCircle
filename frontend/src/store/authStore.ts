import { create } from "zustand";
import type { AuthResponse, AuthUser } from "../api/authApi";
import { tokenStorage } from "../api/tokenStorage";

const USER_KEY = "vibecircle_user";

interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  setSession: (data: AuthResponse) => void;
  clearSession: () => void;
}

function getSavedUser(): AuthUser | null {
  try {
    const saved = sessionStorage.getItem(USER_KEY);
    return saved ? (JSON.parse(saved) as AuthUser) : null;
  } catch {
    return null;
  }
}

const savedUser = getSavedUser();

const useAuthStore = create<AuthState>((set) => ({
  user: savedUser,
  isAuthenticated: Boolean(savedUser && tokenStorage.getAccessToken()),

  setSession: (data) => {
    tokenStorage.saveTokens(data.accessToken, data.refreshToken);
    sessionStorage.setItem(USER_KEY, JSON.stringify(data.user));
    set({ user: data.user, isAuthenticated: true });
  },

  clearSession: () => {
    tokenStorage.clearTokens();
    sessionStorage.removeItem(USER_KEY);
    set({ user: null, isAuthenticated: false });
  },
}));

export default useAuthStore;
