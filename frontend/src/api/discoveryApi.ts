import api from "./axios";

export interface DiscoveredUser {
  userId: number;
  username: string;
  fullName: string;
  bio: string | null;
  city: {
    id: number;
    name: string;
  } | null;
  sharedInterests: string[];
  sharedCount: number;
  similarityPercent: number;
}

export interface DiscoveryPage {
  items: DiscoveredUser[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface DiscoveryParams {
  page?: number;
  size?: number;
  interestId?: number;
  cityId?: number;
  search?: string;
}

export const discoveryApi = {
  getPeople: async (params: DiscoveryParams = {}) => {
    const response = await api.get<DiscoveryPage>("/api/discovery", {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        ...(params.interestId != null ? { interestId: params.interestId } : {}),
        ...(params.cityId != null ? { cityId: params.cityId } : {}),
        ...(params.search?.trim() ? { q: params.search.trim() } : {}),
      },
    });

    return response.data;
  },

  hidePerson: async (userId: number) => {
    await api.post(`/api/discovery/hidden/${userId}`);
  },

  unhidePerson: async (userId: number) => {
    await api.delete(`/api/discovery/hidden/${userId}`);
  },
};