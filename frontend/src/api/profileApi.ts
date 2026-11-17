
import api from "./axios";

export interface City {
  id: number;
  name: string;
  state: string;
  country: string;
}

export interface Interest {
  id: number;
  name: string;
  category: string;
}

export interface ProfileResponse {
  userId: number;
  username: string;
  fullName: string;
  bio: string | null;
  city: City | null;
  profileVisibility: string;
  discoverable: boolean;
  friendRequestPolicy: string;
  interests: Interest[];
  profileComplete: boolean;
}

export interface UpdateProfileRequest {
  bio?: string;
  cityId?: number;
  profileVisibility?: string;
  discoverable?: boolean;
  friendRequestPolicy?: string;
}

export const profileApi = {
  getMyProfile: async (): Promise<ProfileResponse> => {
    const response = await api.get<ProfileResponse>("/api/profiles/me");
    return response.data;
  },

  updateProfile: async (
    data: UpdateProfileRequest
  ): Promise<ProfileResponse> => {
    const response = await api.patch<ProfileResponse>(
      "/api/profiles/me",
      data
    );
    return response.data;
  },

  updateInterests: async (
    interestIds: number[]
  ): Promise<ProfileResponse> => {
    const response = await api.put<ProfileResponse>(
      "/api/profiles/me/interests",
      { interestIds }
    );
    return response.data;
  },
};
