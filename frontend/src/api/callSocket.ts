import { Client, type IMessage } from "@stomp/stompjs";
import { tokenStorage } from "./tokenStorage";
import type { CallView } from "./callApi";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const WS_URL = API_BASE_URL.replace(/^http/, "ws") + "/ws";

export type SignalType =
  | "OFFER"
  | "ANSWER"
  | "ICE_CANDIDATE"
  | "MEDIA_STATE";

export interface CallEvent {
  type:
    | "INCOMING"
    | "ACCEPTED"
    | "REJECTED"
    | "CANCELLED"
    | "MISSED"
    | "ENDED"
    | "FAILED"
    | "SIGNAL";
  callId: number;
  call?: CallView;
  from?: number;
  signalType?: SignalType;
  data?: string;
}

interface CallSocketCallbacks {
  onCallEvent: (event: CallEvent) => void;
  onError: (error: unknown) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
}

export function createCallSocket(callbacks: CallSocketCallbacks) {
  const client = new Client({
    brokerURL: WS_URL,
    beforeConnect: () => {
      const token = tokenStorage.getAccessToken();
      if (!token) throw new Error("No access token available for calls.");
      client.connectHeaders = { Authorization: `Bearer ${token}` };
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
    onConnect: () => {
      callbacks.onConnected?.();
      client.subscribe("/user/queue/call", (message: IMessage) => {
        try {
          callbacks.onCallEvent(JSON.parse(message.body) as CallEvent);
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
    onDisconnect: () => callbacks.onDisconnected?.(),
    onWebSocketClose: () => callbacks.onDisconnected?.(),
    onStompError: (frame) =>
      callbacks.onError(frame.headers.message || frame.body),
  });

  return {
    client,
    connect: () => client.activate(),
    disconnect: () => client.deactivate(),
    sendSignal: (callId: number, type: SignalType, data: unknown) => {
      if (!client.connected) throw new Error("Call connection is not ready.");
      client.publish({
        destination: "/app/call/signal",
        body: JSON.stringify({
          callId,
          type,
          data: JSON.stringify(data),
        }),
      });
    },
  };
}
