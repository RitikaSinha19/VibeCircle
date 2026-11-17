
import api from "./axios";

export interface CommentAuthor {
  id: number;
  username: string;
  fullName: string;
}

export interface CommentView {
  id: number;
  postId: number;
  author: CommentAuthor;
  body: string;
  mine: boolean;
  createdAt: string;
}

export interface CommentPage {
  items: CommentView[];
  page: number;
  size: number;
  hasNext: boolean;
}

export const commentApi = {
  getComments: async (
    postId: number,
    page = 0,
    size = 20
  ): Promise<CommentPage> => {
    const response = await api.get<CommentPage>(
      `/api/posts/${postId}/comments`,
      { params: { page, size } }
    );

    return response.data;
  },

  createComment: async (
    postId: number,
    body: string
  ): Promise<CommentView> => {
    const response = await api.post<CommentView>(
      `/api/posts/${postId}/comments`,
      { body }
    );

    return response.data;
  },

  deleteComment: async (
    postId: number,
    commentId: number
  ): Promise<void> => {
    await api.delete(
      `/api/posts/${postId}/comments/${commentId}`
    );
  },
};
