import { useEffect, useRef } from "react";
import {
  Mic,
  MicOff,
  Video,
  VideoOff,
  PhoneOff,
  Phone,
  X,
} from "lucide-react";
import type { CallView } from "../../api/callApi";

interface CallModalProps {
  call: CallView | null;
  incoming: CallView | null;
  phase: string;
  error: string;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  muted: boolean;
  cameraOff: boolean;
  currentUserId: number | undefined;
  onAccept: (call: CallView) => void;
  onReject: (call: CallView) => void;
  onHangUp: () => void;
  onToggleMute: () => void;
  onToggleCamera: () => void;
  onCloseError: () => void;
}

function VideoTile({
  stream,
  muted,
  label,
}: {
  stream: MediaStream | null;
  muted?: boolean;
  label: string;
}) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.srcObject = stream;
  }, [stream]);

  return (
    <div className="relative flex min-h-40 flex-1 items-center justify-center overflow-hidden rounded-2xl bg-[#17233e]">
      <video
        ref={ref}
        autoPlay
        playsInline
        muted={muted}
        className="h-full max-h-[55vh] w-full object-cover"
      />
      {!stream && <span className="text-sm text-white/70">{label}</span>}
      <span className="absolute bottom-3 left-3 rounded-lg bg-black/50 px-2 py-1 text-xs text-white">
        {label}
      </span>
    </div>
  );
}

export default function CallModal(props: CallModalProps) {
  const {
    call, incoming, phase, error, localStream, remoteStream, muted,
    cameraOff, currentUserId, onAccept, onReject, onHangUp,
    onToggleMute, onToggleCamera, onCloseError,
  } = props;

  if (incoming) {
    const callerName = incoming.caller.fullName;
    return (
      <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4">
        <div className="w-full max-w-sm rounded-3xl bg-[#F5EBDD] p-7 text-center shadow-2xl">
          <div className="mx-auto mb-4 flex h-20 w-20 items-center justify-center rounded-full bg-[#0F1B36] text-white">
            <Phone size={30} />
          </div>
          <p className="text-sm font-semibold uppercase tracking-wider text-[#927B68]">
            Incoming {incoming.type.toLowerCase()} call
          </p>
          <h2 className="mt-2 text-2xl font-bold text-[#0F1B36]">{callerName}</h2>
          <p className="mt-2 text-sm text-[#927B68]">is calling you</p>
          <div className="mt-7 flex justify-center gap-5">
            <button onClick={() => onReject(incoming)} className="flex h-14 w-14 items-center justify-center rounded-full bg-red-600 text-white" aria-label="Reject call">
              <PhoneOff />
            </button>
            <button onClick={() => onAccept(incoming)} className="flex h-14 w-14 items-center justify-center rounded-full bg-green-600 text-white" aria-label="Accept call">
              <Phone />
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!call && phase !== "error") return null;

  const otherName = call
    ? call.caller.id === currentUserId
      ? call.callee.fullName
      : call.caller.fullName
    : "Call";

  const video = call?.type === "VIDEO";

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 p-3 md:p-6">
      <div className="w-full max-w-3xl overflow-hidden rounded-3xl bg-[#F5EBDD] shadow-2xl">
        <div className="flex items-center justify-between border-b border-[#D3BBA3] px-5 py-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-[#927B68]">
              {video ? "Video call" : "Voice call"}
            </p>
            <h2 className="font-bold text-[#0F1B36]">{otherName}</h2>
          </div>
          <button onClick={onCloseError} className="rounded-lg p-2 text-[#927B68] hover:bg-[#E9D8C5]" aria-label="Close call error">
            <X size={19} />
          </button>
        </div>

        {error && (
          <div className="m-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {video ? (
          <div className="grid gap-3 p-4 md:grid-cols-2">
            <VideoTile stream={remoteStream} label={otherName} />
            <VideoTile stream={localStream} muted label="You" />
            {cameraOff && (
              <p className="col-span-full text-center text-xs text-[#927B68]">Your camera is off.</p>
            )}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
            <div className="flex h-24 w-24 items-center justify-center rounded-full bg-[#0F1B36] text-3xl font-bold text-white">
              {otherName.slice(0, 1).toUpperCase()}
            </div>
            <p className="mt-5 text-xl font-bold text-[#0F1B36]">{otherName}</p>
            <p className="mt-2 text-sm capitalize text-[#927B68]">
              {phase === "calling" ? "Calling..." : phase === "connecting" ? "Connecting..." : phase === "active" ? "Connected" : "Starting call"}
            </p>
            {remoteStream && (
              <audio autoPlay ref={(node) => { if (node) node.srcObject = remoteStream; }} />
            )}
          </div>
        )}

        <div className="flex items-center justify-center gap-4 border-t border-[#D3BBA3] p-5">
          <button onClick={onToggleMute} className={`flex h-12 w-12 items-center justify-center rounded-full ${muted ? "bg-[#D87355] text-white" : "bg-white text-[#0F1B36]"}`} aria-label={muted ? "Unmute microphone" : "Mute microphone"}>
            {muted ? <MicOff /> : <Mic />}
          </button>
          {video && (
            <button onClick={onToggleCamera} className={`flex h-12 w-12 items-center justify-center rounded-full ${cameraOff ? "bg-[#D87355] text-white" : "bg-white text-[#0F1B36]"}`} aria-label={cameraOff ? "Turn camera on" : "Turn camera off"}>
              {cameraOff ? <VideoOff /> : <Video />}
            </button>
          )}
          <button onClick={onHangUp} className="flex h-12 w-12 items-center justify-center rounded-full bg-red-600 text-white" aria-label="End call">
            <PhoneOff />
          </button>
        </div>
      </div>
    </div>
  );
}
