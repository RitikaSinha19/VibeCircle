
import api from "./axios";

export interface PostAuthor {
  id: number;
  username: string;
  fullName: string;
}

export interface PostMedia {
  id: number;
  type: string;
  url: string;
}

export interface PostView {
  id: number;
  author: PostAuthor;
  caption: string | null;
  audience: "PUBLIC" | "FRIENDS_ONLY";
  cityId: number | null;
  cityName: string | null;
  media: PostMedia[];
  hashtags: string[];
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  mine: boolean;
  createdAt: string;
}

export interface FeedPage {
  items: PostView[];
  nextCursor: number | null;
  hasNext: boolean;
}

export interface LikeResponse {
  liked: boolean;
  likeCount: number;
}

export type FeedScope = "ALL" | "FRIENDS";

export const postApi = {
  getFeed: async (
    scope: FeedScope = "ALL",
    before?: number,
    size = 20
  ): Promise<FeedPage> => {
    const response = await api.get<FeedPage>("/api/feed", {
      params: { scope, before, size },
    });

    return response.data;
  },

  createPost: async (
    caption: string,
    files: File[] = [],
    audience: "PUBLIC" | "FRIENDS_ONLY" = "PUBLIC"
  ): Promise<PostView> => {
    const formData = new FormData();

    if (caption.trim()) {
      formData.append("caption", caption.trim());
    }

    formData.append("audience", audience);

    files.forEach((file) => {
      formData.append("files", file);
    });

    const response = await api.post<PostView>(
      "/api/posts",
      formData,
      {
        headers: {
          "Content-Type": undefined,
        },
      }
    );

    return response.data;
  },

  likePost: async (postId: number): Promise<LikeResponse> => {
    const response = await api.post<LikeResponse>(
      `/api/posts/${postId}/like`
    );

    return response.data;
  },

  unlikePost: async (postId: number): Promise<LikeResponse> => {
    const response = await api.delete<LikeResponse>(
      `/api/posts/${postId}/like`
    );

    return response.data;
  },

  getPost: async (postId: number): Promise<PostView> => {
    const response = await api.get<PostView>(`/api/posts/${postId}`);
    return response.data;
  },

  updatePost: async (
    postId: number,
    data: {
      caption?: string;
      audience?: "PUBLIC" | "FRIENDS_ONLY";
    }
  ): Promise<PostView> => {
    const response = await api.patch<PostView>(
      `/api/posts/${postId}`,
      data
    );

    return response.data;
  },

  deletePost: async (postId: number): Promise<void> => {
    await api.delete(`/api/posts/${postId}`);
  },
};
