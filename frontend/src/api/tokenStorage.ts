
const ACCESS_TOKEN_KEY = "vibecircle_access_token";
const REFRESH_TOKEN_KEY = "vibecircle_refresh_token";

export const tokenStorage = {
  getAccessToken: () => sessionStorage.getItem(ACCESS_TOKEN_KEY),

  getRefreshToken: () => sessionStorage.getItem(REFRESH_TOKEN_KEY),

  saveTokens: (accessToken: string, refreshToken: string) => {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },

  clearTokens: () => {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};
