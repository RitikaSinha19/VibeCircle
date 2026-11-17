
import api from "./axios";

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
  dateOfBirth: string;
}

export interface RegisterResponse {
  id: number;
  username: string;
  message: string;
}

export interface LoginRequest {
  identifier: string;
  password: string;
}

export interface VerifyEmailRequest {
  username: string;
  otp: string;
}

export interface AuthUser {
  id: number;
  username: string;
  fullName: string;
  role: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export const authApi = {
  register: async (data: RegisterRequest) => {
    const response = await api.post<RegisterResponse>(
      "/api/auth/register",
      data
    );

    return response.data;
  },

  verifyEmail: async (data: VerifyEmailRequest) => {
    await api.post("/api/auth/verify-email", data);
  },

  login: async (data: LoginRequest) => {
    const response = await api.post<AuthResponse>(
      "/api/auth/login",
      data
    );

    return response.data;
  },

  refresh: async (refreshToken: string) => {
    const response = await api.post<AuthResponse>(
      "/api/auth/refresh",
      { refreshToken }
    );

    return response.data;
  },

  logout: async (refreshToken: string) => {
    await api.post("/api/auth/logout", { refreshToken });
  },
};
