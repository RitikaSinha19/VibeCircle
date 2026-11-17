import { useCallback, useEffect, useRef, useState } from "react";
import { callApi, type CallType, type CallView } from "../api/callApi";
import {
  type CallEvent,
  type SignalType,
} from "../api/callSocket";

export interface CallSocketHandle {
  client: { connected: boolean };
  sendSignal: (callId: number, type: SignalType, data: unknown) => void;
}

type CallPhase =
  | "idle"
  | "calling"
  | "ringing"
  | "connecting"
  | "active"
  | "error";

function participantName(call: CallView, currentUserId: number) {
  return call.caller.id === currentUserId
    ? call.callee.fullName
    : call.caller.fullName;
}

export function useWebRTCCall(
  currentUserId: number | undefined,
  socket: CallSocketHandle | null
) {
  const [call, setCall] = useState<CallView | null>(null);
  const [phase, setPhase] = useState<CallPhase>("idle");
  const [error, setError] = useState("");
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [muted, setMuted] = useState(false);
  const [cameraOff, setCameraOff] = useState(false);
  const [incoming, setIncoming] = useState<CallView | null>(null);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const pendingCandidatesRef = useRef<RTCIceCandidateInit[]>([]);
  const pendingSignalsRef = useRef<CallEvent[]>([]);
  const callRef = useRef<CallView | null>(null);
  const ignoredCallIdsRef = useRef<Set<number>>(new Set());
  const makingOfferRef = useRef(false);

  useEffect(() => {
    callRef.current = call;
  }, [call]);

  const closePeer = useCallback(() => {
    pcRef.current?.close();
    pcRef.current = null;
    localStreamRef.current?.getTracks().forEach((track) => track.stop());
    localStreamRef.current = null;
    setLocalStream(null);
    setRemoteStream(null);
    pendingCandidatesRef.current = [];
    pendingSignalsRef.current = [];
    makingOfferRef.current = false;
    setMuted(false);
    setCameraOff(false);
  }, []);

  const reset = useCallback(() => {
    closePeer();
    setCall(null);
    setIncoming(null);
    setPhase("idle");
    setError("");
  }, [closePeer]);

  const sendSignal = useCallback(
    (id: number, type: SignalType, data: unknown) => {
      if (!socket?.client.connected) {
        throw new Error("Call signalling is disconnected. Reconnecting...");
      }
      socket.sendSignal(id, type, data);
    },
    [socket]
  );

  const createPeer = useCallback(
    async (activeCall: CallView) => {
      if (pcRef.current) return pcRef.current;

      const ice = await callApi.iceServers();
      const pc = new RTCPeerConnection({
        iceServers: ice.iceServers.map((server) => ({
          urls: server.urls,
          ...(server.username ? { username: server.username } : {}),
          ...(server.credential ? { credential: server.credential } : {}),
        })),
      });

      pc.onicecandidate = (event) => {
        if (event.candidate) {
          try {
            sendSignal(activeCall.id, "ICE_CANDIDATE", event.candidate.toJSON());
          } catch (e) {
            setError(e instanceof Error ? e.message : "Could not send network candidate.");
          }
        }
      };

      pc.ontrack = (event) => {
        const stream = event.streams[0];
        if (stream) setRemoteStream(stream);
      };

      pc.onconnectionstatechange = () => {
        if (pc.connectionState === "connected") {
          setPhase("active");
          setError("");
        } else if (
          pc.connectionState === "failed" ||
          pc.connectionState === "disconnected"
        ) {
          setError("Media connection interrupted. Check your network and permissions.");
        }
      };

      pcRef.current = pc;
      return pc;
    },
    [sendSignal]
  );

  const prepareMedia = useCallback(
    async (activeCall: CallView) => {
      if (localStreamRef.current) return localStreamRef.current;
      const audio = true;
      const video = activeCall.type === "VIDEO";
      const stream = await navigator.mediaDevices.getUserMedia({ audio, video });
      localStreamRef.current = stream;
      setLocalStream(stream);
      return stream;
    },
    []
  );

  const addLocalTracks = useCallback(
    (pc: RTCPeerConnection, stream: MediaStream) => {
      stream.getTracks().forEach((track) => {
        if (!pc.getSenders().some((sender) => sender.track?.id === track.id)) {
          pc.addTrack(track, stream);
        }
      });
    },
    []
  );

  const flushCandidates = useCallback(async (pc: RTCPeerConnection) => {
    const queued = pendingCandidatesRef.current.splice(0);
    for (const candidate of queued) {
      await pc.addIceCandidate(new RTCIceCandidate(candidate));
    }
  }, []);

  const processSignal = useCallback(
    async (event: CallEvent) => {
      const activeCall = callRef.current;
      if (!activeCall || activeCall.id !== event.callId || !event.signalType || !event.data) {
        pendingSignalsRef.current.push(event);
        return;
      }

      const signalData = JSON.parse(event.data) as
        | RTCSessionDescriptionInit
        | RTCIceCandidateInit
        | { muted?: boolean; cameraOff?: boolean };

      if (event.signalType === "OFFER") {
        const pc = await createPeer(activeCall);
        const stream = await prepareMedia(activeCall);
        addLocalTracks(pc, stream);
        await pc.setRemoteDescription(
          new RTCSessionDescription(signalData as RTCSessionDescriptionInit)
        );
        await flushCandidates(pc);
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        sendSignal(activeCall.id, "ANSWER", answer);
        setPhase("connecting");
      } else if (event.signalType === "ANSWER") {
        const pc = pcRef.current;
        if (!pc) return;
        await pc.setRemoteDescription(
          new RTCSessionDescription(signalData as RTCSessionDescriptionInit)
        );
        await flushCandidates(pc);
        setPhase("connecting");
      } else if (event.signalType === "ICE_CANDIDATE") {
        const candidate = signalData as RTCIceCandidateInit;
        const pc = pcRef.current;
        if (pc?.remoteDescription) {
          await pc.addIceCandidate(new RTCIceCandidate(candidate));
        } else {
          pendingCandidatesRef.current.push(candidate);
        }
      }
    },
    [addLocalTracks, createPeer, flushCandidates, prepareMedia, sendSignal]
  );

  const processPendingSignals = useCallback(async () => {
    const queued = pendingSignalsRef.current.splice(0);
    for (const event of queued) await processSignal(event);
  }, [processSignal]);

  const startCall = useCallback(
    async (calleeId: number, type: CallType) => {
      if (!currentUserId) return;
      if (!socket?.client.connected) {
        setError("Call channel is still connecting. Please try again.");
        return;
      }
      try {
        setError("");
        const created = await callApi.start({ calleeId, type });
        setCall(created);
        callRef.current = created;
        setPhase("calling");
        const stream = await prepareMedia(created);
        const pc = await createPeer(created);
        addLocalTracks(pc, stream);
        makingOfferRef.current = true;
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        sendSignal(created.id, "OFFER", offer);
        makingOfferRef.current = false;
      } catch (e) {
        const message = e instanceof Error ? e.message : "Could not start call.";
        setError(message);
        setPhase("error");
        const active = callRef.current;
        if (active) {
          callApi.failed(active.id).catch(() => undefined);
        }
      }
    },
    [addLocalTracks, createPeer, currentUserId, prepareMedia, sendSignal, socket]
  );

  const acceptCall = useCallback(
    async (incomingCall: CallView) => {
      try {
        setError("");
        ignoredCallIdsRef.current.delete(incomingCall.id);
        const accepted = await callApi.accept(incomingCall.id);
        setCall(accepted);
        callRef.current = accepted;
        setIncoming(null);
        setPhase("connecting");
        const stream = await prepareMedia(accepted);
        const pc = await createPeer(accepted);
        addLocalTracks(pc, stream);
        await processPendingSignals();
      } catch (e) {
        setError(e instanceof Error ? e.message : "Could not accept call.");
        callApi.failed(incomingCall.id).catch(() => undefined);
        reset();
      }
    },
    [addLocalTracks, createPeer, prepareMedia, processPendingSignals, reset]
  );

  const rejectCall = useCallback(async (incomingCall: CallView) => {
    ignoredCallIdsRef.current.add(incomingCall.id);
    setIncoming(null);
    try {
      await callApi.reject(incomingCall.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not reject call.");
    }
  }, []);

  const hangUp = useCallback(async () => {
    const active = callRef.current;
    if (!active) return;
    const caller = currentUserId === active.caller.id;
    const pending = active.status === "RINGING" || phase === "calling";
    try {
      if (pending && caller) await callApi.cancel(active.id);
      else await callApi.end(active.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not end call.");
    } finally {
      reset();
    }
  }, [currentUserId, phase, reset]);

  const toggleMute = useCallback(() => {
    const next = !muted;
    localStreamRef.current?.getAudioTracks().forEach((track) => {
      track.enabled = !next;
    });
    setMuted(next);
    const active = callRef.current;
    if (active) {
      try {
        sendSignal(active.id, "MEDIA_STATE", { muted: next, cameraOff });
      } catch { /* UI state still updates locally. */ }
    }
  }, [cameraOff, muted, sendSignal]);

  const toggleCamera = useCallback(() => {
    const next = !cameraOff;
    localStreamRef.current?.getVideoTracks().forEach((track) => {
      track.enabled = !next;
    });
    setCameraOff(next);
    const active = callRef.current;
    if (active) {
      try {
        sendSignal(active.id, "MEDIA_STATE", { muted, cameraOff: next });
      } catch { /* UI state still updates locally. */ }
    }
  }, [cameraOff, muted, sendSignal]);

  const handleCallEvent = useCallback(
    async (event: CallEvent) => {
      if (event.type === "SIGNAL") {
        if (ignoredCallIdsRef.current.has(event.callId)) return;
        await processSignal(event);
        return;
      }

      if (event.type === "INCOMING" && event.call) {
        if (ignoredCallIdsRef.current.has(event.callId)) return;
        setIncoming(event.call);
        return;
      }

      if (event.call) {
        setCall((previous) =>
          previous?.id === event.callId ? event.call! : previous
        );
      }

      if (event.type === "ACCEPTED" && callRef.current?.id === event.callId) {
        setPhase("connecting");
      }

      if (
        ["REJECTED", "CANCELLED", "MISSED", "ENDED", "FAILED"].includes(event.type) &&
        (callRef.current?.id === event.callId || incoming?.id === event.callId)
      ) {
        reset();
      }
    },
    [incoming?.id, processSignal, reset]
  );

  useEffect(() => {
    return () => closePeer();
  }, [closePeer]);

  return {
    call,
    incoming,
    phase,
    error,
    localStream,
    remoteStream,
    muted,
    cameraOff,
    otherParticipantName:
      call && currentUserId ? participantName(call, currentUserId) : "",
    startCall,
    acceptCall,
    rejectCall,
    hangUp,
    toggleMute,
    toggleCamera,
    handleCallEvent,
    clearError: () => setError(""),
  };
}
