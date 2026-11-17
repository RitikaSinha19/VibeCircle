
import api from "./axios";

// -------------------- Types matching the backend DTOs --------------------

export interface ChatUser {
  id: number;
  username: string;
  fullName: string;
}

export interface Conversation {
  id: number;
  otherUser: ChatUser;
  lastMessagePreview: string | null;
  lastMessageSenderId: number | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface Message {
  id: number;
  conversationId: number;
  senderId: number;
  type: "TEXT" | "IMAGE";
  text: string | null;
  imageUrl: string | null;
  unsent: boolean;
  status: "SENT" | "DELIVERED" | "READ" | null;
  clientMsgId: string | null;
  createdAt: string;
}

export interface MessagePage {
  items: Message[];
  nextBefore: number | null;
  hasNext: boolean;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface Presence {
  userId: number;
  online: boolean;
  lastSeenAt: string | null;
}

// -------------------- Chat REST API --------------------

export const chatApi = {
  // Get the logged-in user's conversations.
  getConversations: async (page = 0, size = 20) => {
    const response = await api.get<PageResult<Conversation>>(
      "/api/chat/conversations",
      { params: { page, size } }
    );

    return response.data;
  },

  // Open an existing conversation or create one with a friend.
  openConversation: async (userId: number) => {
    const response = await api.post<Conversation>(
      "/api/chat/conversations",
      { userId }
    );

    return response.data;
  },

  // Get message history. The backend returns newest messages first.
  getMessages: async (
    conversationId: number,
    before?: number,
    size = 30
  ) => {
    const response = await api.get<MessagePage>(
      `/api/chat/conversations/${conversationId}/messages`,
      {
        params: {
          ...(before !== undefined ? { before } : {}),
          size,
        },
      }
    );

    return response.data;
  },

  // Send a text message through REST.
  // WebSocket sending will be added separately.
  sendMessage: async (
    conversationId: number,
    text: string,
    clientMsgId: string
  ) => {
    const response = await api.post<Message>(
      `/api/chat/conversations/${conversationId}/messages`,
      { text, clientMsgId }
    );

    return response.data;
  },

  // Send an image.
  sendImage: async (conversationId: number, file: File) => {
    const formData = new FormData();
    formData.append("file", file);

    const response = await api.post<Message>(
      `/api/chat/conversations/${conversationId}/images`,
      formData
    );

    return response.data;
  },

  // Mark messages as read.
  markRead: async (
    conversationId: number,
    upToMessageId?: number
  ) => {
    await api.post(
      `/api/chat/conversations/${conversationId}/read`,
      upToMessageId !== undefined ? { upToMessageId } : {}
    );
  },

  // Unsend a message for everyone.
  unsendMessage: async (messageId: number) => {
    await api.delete(`/api/chat/messages/${messageId}`);
  },

  // Hide a message only for the logged-in user.
  hideMessage: async (messageId: number) => {
    await api.post(`/api/chat/messages/${messageId}/hide`);
  },

  // Get online status for a list of friends.
  getPresence: async (userIds: number[]) => {
    if (userIds.length === 0) return [];

    const response = await api.get<Presence[]>("/api/chat/presence", {
      params: { userIds: userIds.join(",") },
    });

    return response.data;
  },
};
