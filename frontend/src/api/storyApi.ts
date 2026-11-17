import api from "./axios";

export interface StoryAuthor {
  id: number;
  username: string;
  fullName: string;
}

export interface StoryView {
  id: number;
  author: StoryAuthor;
  text: string | null;
  mediaType: string | null;
  mediaUrl: string | null;
  audience: "PUBLIC" | "FRIENDS_ONLY";
  createdAt: string;
  expiresAt: string;
  viewedByMe: boolean;
  viewCount: number | null;
  mine: boolean;
}

export interface StoryTrayItem {
  author: StoryAuthor;
  storyCount: number;
  hasUnseen: boolean;
  latestAt: string;
}

interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export const storyApi = {
  getTray: async (): Promise<StoryTrayItem[]> => {
    const response = await api.get<PageResult<StoryTrayItem>>("/api/stories/tray", {
      params: { page: 0, size: 20 },
    });
    return response.data.items ?? [];
  },

  getMine: async (): Promise<StoryView[]> => {
    const response = await api.get<StoryView[]>("/api/stories/mine");
    return response.data;
  },

  getByUser: async (userId: number): Promise<StoryView[]> => {
    const response = await api.get<StoryView[]>(`/api/users/${userId}/stories`);
    return response.data;
  },

  create: async (text: string, file: File | null, audience: "PUBLIC" | "FRIENDS_ONLY" = "FRIENDS_ONLY"): Promise<StoryView> => {
    const form = new FormData();
    if (text.trim()) form.append("text", text.trim());
    form.append("audience", audience);
    if (file) form.append("file", file);
    const response = await api.post<StoryView>("/api/stories", form);
    return response.data;
  },

  markViewed: async (storyId: number): Promise<void> => {
    await api.post(`/api/stories/${storyId}/view`);
  },

  getMedia: async (mediaUrl: string): Promise<string> => {
    const response = await api.get(mediaUrl, { responseType: "blob" });
    return URL.createObjectURL(response.data as Blob);
  },
};
