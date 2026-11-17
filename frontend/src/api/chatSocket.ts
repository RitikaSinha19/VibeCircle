
import { Client, type IMessage } from "@stomp/stompjs";
import { tokenStorage } from "./tokenStorage";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const WS_URL = API_BASE_URL.replace(/^http/, "ws") + "/ws";

export interface ChatEvent {
  type: "MESSAGE" | "READ" | "DELIVERED" | "UNSENT";
  conversationId: number;
  message?: {
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
  };
  userId?: number;
  upToMessageId?: number;
  messageIds?: number[];
}

export interface TypingEvent {
  conversationId: number;
  userId: number;
  typing: boolean;
}

export interface PresenceEvent {
  userId: number;
  online: boolean;
  lastSeenAt: string | null;
}

export interface ChatSocketCallbacks {
  onMessage: (event: ChatEvent) => void;
  onTyping: (event: TypingEvent) => void;
  onPresence: (event: PresenceEvent) => void;
  onError: (error: unknown) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
}

export function createChatSocket(callbacks: ChatSocketCallbacks) {
  const client = new Client({
    brokerURL: WS_URL,

    // Fetch the latest token whenever STOMP connects or reconnects.
    beforeConnect: () => {
      const token = tokenStorage.getAccessToken();

      if (!token) {
        throw new Error("No access token available for chat connection.");
      }

      client.connectHeaders = {
        Authorization: `Bearer ${token}`,
      };
    },

    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    onConnect: () => {
      callbacks.onConnected?.();

      client.subscribe("/user/queue/messages", (message: IMessage) => {
        try {
          callbacks.onMessage(JSON.parse(message.body) as ChatEvent);
        } catch (error) {
          callbacks.onError(error);
        }
      });

      client.subscribe("/user/queue/typing", (message: IMessage) => {
        try {
          callbacks.onTyping(JSON.parse(message.body) as TypingEvent);
        } catch (error) {
          callbacks.onError(error);
        }
      });

      client.subscribe("/user/queue/presence", (message: IMessage) => {
        try {
          callbacks.onPresence(JSON.parse(message.body) as PresenceEvent);
        } catch (error) {
          callbacks.onError(error);
        }
      });

      client.subscribe("/user/queue/errors", (message: IMessage) => {
        try {
          callbacks.onError(JSON.parse(message.body));
        } catch {
          callbacks.onError(message.body);
        }
      });
    },

    onDisconnect: () => {
      callbacks.onDisconnected?.();
    },

    onWebSocketClose: () => {
      callbacks.onDisconnected?.();
    },

    onStompError: (frame) => {
      callbacks.onError(frame.headers.message || frame.body);
    },

    // Avoid logging headers or message contents, which may contain private data.
    debug: () => {},
  });

  return {
    client,

    connect: () => client.activate(),

    disconnect: () => client.deactivate(),

    sendMessage: (
      conversationId: number,
      text: string,
      clientMsgId: string
    ) => {
      if (!client.connected) return;

      client.publish({
        destination: "/app/chat/send",
        body: JSON.stringify({
          conversationId,
          text,
          clientMsgId,
        }),
      });
    },

    sendTyping: (conversationId: number, typing: boolean) => {
      if (!client.connected) return;

      client.publish({
        destination: "/app/chat/typing",
        body: JSON.stringify({ conversationId, typing }),
      });
    },

    markRead: (conversationId: number, upToMessageId: number) => {
      if (!client.connected) return;

      client.publish({
        destination: "/app/chat/read",
        body: JSON.stringify({ conversationId, upToMessageId }),
      });
    },
  };
}
