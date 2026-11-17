import api from "./axios";

export type CallType = "VOICE" | "VIDEO";
export type CallStatus =
  | "RINGING"
  | "ACTIVE"
  | "ACCEPTED"
  | "REJECTED"
  | "CANCELLED"
  | "MISSED"
  | "ENDED"
  | "FAILED";

export interface CallParticipant {
  id: number;
  username: string;
  fullName: string;
}

export interface CallView {
  id: number;
  type: CallType;
  status: CallStatus;
  caller: CallParticipant;
  callee: CallParticipant;
  createdAt: string;
  answeredAt: string | null;
  endedAt: string | null;
  durationSeconds: number | null;
}

export interface IceServer {
  urls: string[];
  username: string | null;
  credential: string | null;
}

export interface IceConfig {
  iceServers: IceServer[];
  ttlSeconds: number;
}

export interface StartCallRequest {
  calleeId: number;
  type: CallType;
}

export const callApi = {
  start: async (data: StartCallRequest): Promise<CallView> => {
    const response = await api.post<CallView>("/api/calls", data);
    return response.data;
  },
  get: async (id: number): Promise<CallView> => {
    const response = await api.get<CallView>(`/api/calls/${id}`);
    return response.data;
  },
  history: async (page = 0, size = 20) => {
    const response = await api.get("/api/calls", { params: { page, size } });
    return response.data;
  },
  accept: async (id: number): Promise<CallView> => {
    const response = await api.post<CallView>(`/api/calls/${id}/accept`);
    return response.data;
  },
  reject: async (id: number): Promise<CallView> => {
    const response = await api.post<CallView>(`/api/calls/${id}/reject`);
    return response.data;
  },
  cancel: async (id: number): Promise<CallView> => {
    const response = await api.post<CallView>(`/api/calls/${id}/cancel`);
    return response.data;
  },
  end: async (id: number): Promise<CallView> => {
    const response = await api.post<CallView>(`/api/calls/${id}/end`);
    return response.data;
  },
  failed: async (id: number): Promise<CallView> => {
    const response = await api.post<CallView>(`/api/calls/${id}/failed`);
    return response.data;
  },
  iceServers: async (): Promise<IceConfig> => {
    const response = await api.get<IceConfig>("/api/calls/ice-servers");
    return response.data;
  },
};
