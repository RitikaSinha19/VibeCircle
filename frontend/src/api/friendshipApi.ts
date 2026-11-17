
import api from "./axios";

export interface Friend {
  userId: number;
  username: string;
  fullName: string;
  since: string;
}

export interface FriendRequest {
  id: number;
  otherUserId: number;
  otherUsername: string;
  otherFullName: string;
  status: string;
  createdAt: string;
}

export interface FriendRequestResult {
  outcome: "REQUEST_SENT" | "FRIENDS";
  requestId: number;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export const friendshipApi = {
  getFriends: async (page = 0, size = 20) => {
    const response = await api.get<PageResult<Friend>>(
      "/api/friends",
      { params: { page, size } }
    );
    return response.data;
  },

  getReceivedRequests: async (page = 0, size = 20) => {
    const response = await api.get<PageResult<FriendRequest>>(
      "/api/friends/requests/received",
      { params: { page, size } }
    );
    return response.data;
  },

  getSentRequests: async (page = 0, size = 20) => {
    const response = await api.get<PageResult<FriendRequest>>(
      "/api/friends/requests/sent",
      { params: { page, size } }
    );
    return response.data;
  },

  sendRequest: async (receiverId: number) => {
    const response = await api.post<FriendRequestResult>(
      "/api/friends/requests",
      { receiverId }
    );
    return response.data;
  },

  acceptRequest: async (requestId: number) => {
    const response = await api.post<Friend>(
      `/api/friends/requests/${requestId}/accept`
    );
    return response.data;
  },

  rejectRequest: async (requestId: number) => {
    await api.post(
      `/api/friends/requests/${requestId}/reject`
    );
  },

  cancelRequest: async (requestId: number) => {
    await api.post(
      `/api/friends/requests/${requestId}/cancel`
    );
  },

  removeFriend: async (userId: number) => {
    await api.delete(`/api/friends/${userId}`);
  },
};
