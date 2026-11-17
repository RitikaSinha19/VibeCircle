
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { FormEvent } from "react";
import {
  Search,
  Send,
  Phone,
  Video,
  MoreVertical,
  Paperclip,
  Smile,
  ArrowLeft,
  CheckCheck,
  LoaderCircle,
} from "lucide-react";

import Sidebar from "../components/dashboard/Sidebar";
import { chatApi } from "../api/chatApi";
import type {
  Conversation as ApiConversation,
  Message as ApiMessage,
  Presence,
} from "../api/chatApi";
import {
  createChatSocket,
  type ChatEvent,
  type TypingEvent,
  type PresenceEvent,
} from "../api/chatSocket";
import useAuthStore from "../store/authStore";
import { createCallSocket } from "../api/callSocket";
import type { CallEvent } from "../api/callSocket";
import { useWebRTCCall } from "../hooks/useWebRTCCall";
import CallModal from "../components/calls/CallModal";

function formatTime(value: string | null) {
  if (!value) return "";

  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatLastTime(value: string | null) {
  if (!value) return "";

  const date = new Date(value);
  const today = new Date();

  if (date.toDateString() === today.toDateString()) {
    return formatTime(value);
  }

  return date.toLocaleDateString([], {
    day: "numeric",
    month: "short",
  });
}

function getInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function MessagesPage() {
  const currentUser = useAuthStore((state) => state.user);
  const currentUserId = currentUser?.id;

  const [conversations, setConversations] = useState<ApiConversation[]>([]);
  const [messages, setMessages] = useState<ApiMessage[]>([]);
  const [presence, setPresence] = useState<Record<number, boolean>>({});
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const [search, setSearch] = useState("");
  const [messageText, setMessageText] = useState("");
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [socketConnected, setSocketConnected] = useState(false);
  const [typingUserId, setTypingUserId] = useState<number | null>(null);
  const [error, setError] = useState("");

  const socketRef = useRef<ReturnType<typeof createChatSocket> | null>(null);
  const messageEndRef = useRef<HTMLDivElement | null>(null);
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const callSocketRef = useRef<ReturnType<typeof createCallSocket> | null>(null);
  const callEventHandlerRef = useRef<(event: CallEvent) => void>(() => undefined);
  const [callSocketConnected, setCallSocketConnected] = useState(false);

  const callSocketHandle = callSocketRef.current
    ? {
        client: callSocketRef.current.client,
        sendSignal: callSocketRef.current.sendSignal,
      }
    : null;
  const callControls = useWebRTCCall(currentUserId, callSocketHandle);

  const selectedConversation = conversations.find(
    (conversation) => conversation.id === selectedId
  );

  const filteredConversations = useMemo(() => {
    return conversations.filter((conversation) =>
      conversation.otherUser.fullName
        .toLowerCase()
        .includes(search.toLowerCase())
    );
  }, [conversations, search]);

  const updateConversationPreview = useCallback(
    (message: ApiMessage) => {
      setConversations((previous) =>
        previous.map((conversation) =>
          conversation.id === message.conversationId
            ? {
                ...conversation,
                lastMessagePreview: message.unsent
                  ? "Message unsent"
                  : message.type === "IMAGE"
                    ? "📷 Image"
                    : message.text,
                lastMessageSenderId: message.senderId,
                lastMessageAt: message.createdAt,
                unreadCount:
                  message.senderId !== currentUserId &&
                  conversation.id !== selectedId
                    ? conversation.unreadCount + 1
                    : conversation.unreadCount,
              }
            : conversation
        )
      );
    },
    [currentUserId, selectedId]
  );

  const handleChatEvent = useCallback(
    (event: ChatEvent) => {
      if (event.type === "MESSAGE" && event.message) {
        const incoming = event.message;

        setMessages((previous) => {
          if (previous.some((message) => message.id === incoming.id)) {
            return previous;
          }

          return [...previous, incoming];
        });

        updateConversationPreview(incoming);

        if (
          incoming.conversationId === selectedId &&
          incoming.senderId !== currentUserId
        ) {
          chatApi.markRead(incoming.conversationId, incoming.id).catch(
            console.error
          );
        }
      }

      if (event.type === "DELIVERED" && event.messageIds) {
        setMessages((previous) =>
          previous.map((message) =>
            event.messageIds!.includes(message.id)
              ? { ...message, status: "DELIVERED" }
              : message
          )
        );
      }

      if (event.type === "READ" && event.upToMessageId) {
        setMessages((previous) =>
          previous.map((message) =>
            message.senderId === currentUserId &&
            message.conversationId === event.conversationId &&
            message.id <= event.upToMessageId!
              ? { ...message, status: "READ" }
              : message
          )
        );
      }

      if (event.type === "UNSENT" && event.messageIds) {
        setMessages((previous) =>
          previous.map((message) =>
            event.messageIds!.includes(message.id)
              ? { ...message, unsent: true, text: null }
              : message
          )
        );
      }
    },
    [currentUserId, selectedId, updateConversationPreview]
  );

  const handleTypingEvent = useCallback(
    (event: TypingEvent) => {
      if (event.userId === currentUserId) return;
      if (event.conversationId !== selectedId) return;

      setTypingUserId(event.typing ? event.userId : null);
    },
    [currentUserId, selectedId]
  );

  const handlePresenceEvent = useCallback((event: PresenceEvent) => {
    setPresence((previous) => ({
      ...previous,
      [event.userId]: event.online,
    }));
  }, []);

  // Load conversations from the backend.
  useEffect(() => {
    let active = true;

    async function loadConversations() {
      try {
        setLoadingConversations(true);
        setError("");

        const result = await chatApi.getConversations(0, 20);

        if (!active) return;

        setConversations(result.items);

        setSelectedId((previous) => {
          if (previous && result.items.some((item) => item.id === previous)) {
            return previous;
          }

          return result.items[0]?.id ?? null;
        });
      } catch (err) {
        console.error("Could not load conversations:", err);

        if (active) {
          setError("Could not load your conversations. Please try again.");
        }
      } finally {
        if (active) setLoadingConversations(false);
      }
    }

    loadConversations();

    return () => {
      active = false;
    };
  }, []);

  // Keep the latest event handlers available without reconnecting the socket
  // whenever the user switches conversations.
  const chatEventHandlerRef = useRef(handleChatEvent);
  const typingEventHandlerRef = useRef(handleTypingEvent);
  const presenceEventHandlerRef = useRef(handlePresenceEvent);

  chatEventHandlerRef.current = handleChatEvent;
  typingEventHandlerRef.current = handleTypingEvent;
  presenceEventHandlerRef.current = handlePresenceEvent;

  // Connect the authenticated WebSocket once per signed-in user.
  useEffect(() => {
    if (!currentUserId) return;

    const socket = createChatSocket({
      onMessage: (event) => chatEventHandlerRef.current(event),
      onTyping: (event) => typingEventHandlerRef.current(event),
      onPresence: (event) => presenceEventHandlerRef.current(event),
      onError: (socketError) => {
        console.error("Chat socket error:", socketError);
      },
      onConnected: () => setSocketConnected(true),
      onDisconnected: () => setSocketConnected(false),
    });

    socketRef.current = socket;
    socket.connect();

    return () => {
      socketRef.current = null;
      socket.disconnect();
      setSocketConnected(false);
    };
  }, [currentUserId]);

  // Separate authenticated STOMP channel for call state and WebRTC signalling.
  useEffect(() => {
    if (!currentUserId) return;
    const callSocket = createCallSocket({
      onCallEvent: (event) => callEventHandlerRef.current(event),
      onError: (socketError) => {
        console.error("Call socket error:", socketError);
      },
      onConnected: () => setCallSocketConnected(true),
      onDisconnected: () => setCallSocketConnected(false),
    });
    callSocketRef.current = callSocket;
    callSocket.connect();
    return () => {
      callSocketRef.current = null;
      setCallSocketConnected(false);
      callSocket.disconnect();
    };
  }, [currentUserId]);

  callEventHandlerRef.current = (event) => {
    void callControls.handleCallEvent(event);
  };

  // Fetch online status for conversation participants.
  useEffect(() => {
    if (conversations.length === 0) return;

    const userIds = conversations.map(
      (conversation) => conversation.otherUser.id
    );

    chatApi
      .getPresence(userIds)
      .then((result: Presence[]) => {
        setPresence((previous) => {
          const next = { ...previous };

          result.forEach((item) => {
            next[item.userId] = item.online;
          });

          return next;
        });
      })
      .catch((err) => {
        console.error("Could not load presence:", err);
      });
  }, [conversations]);

  // Load messages when a conversation is selected.
  useEffect(() => {
    if (!selectedId) {
      setMessages([]);
      return;
    }

    let active = true;

    async function loadMessages() {
      try {
        setLoadingMessages(true);
        setError("");

        const result = await chatApi.getMessages(selectedId!, undefined, 30);

        if (!active) return;

        // The backend returns newest messages first.
        const orderedMessages = [...result.items].reverse();
        setMessages(orderedMessages);

        const latestMessage = orderedMessages.at(-1);

        if (latestMessage) {
          await chatApi.markRead(selectedId!, latestMessage.id);
        } else {
          await chatApi.markRead(selectedId!);
        }

        setConversations((previous) =>
          previous.map((conversation) =>
            conversation.id === selectedId
              ? { ...conversation, unreadCount: 0 }
              : conversation
          )
        );
      } catch (err) {
        console.error("Could not load messages:", err);

        if (active) {
          setError("Could not load messages. Please try again.");
        }
      } finally {
        if (active) setLoadingMessages(false);
      }
    }

    loadMessages();

    return () => {
      active = false;
    };
  }, [selectedId]);

  // Keep the latest message visible.
  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, selectedId]);

  const handleSelectConversation = (id: number) => {
    setSelectedId(id);
    setTypingUserId(null);
    setMessageText("");
  };

  const handleSendMessage = async (
    event: FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    const trimmedMessage = messageText.trim();

    if (!trimmedMessage || !selectedId || sending) return;

    if (!socketRef.current?.client.connected) {
      setError("Chat is reconnecting. Please try again in a moment.");
      return;
    }

    try {
      setSending(true);
      setError("");

      const clientMsgId = crypto.randomUUID();

      socketRef.current.sendMessage(
        selectedId,
        trimmedMessage,
        clientMsgId
      );

      setMessageText("");
    } catch (err) {
      console.error("Could not send message:", err);
      setError("Message could not be sent. Please try again.");
    } finally {
      setSending(false);
    }
  };

  const handleTyping = (value: string) => {
    setMessageText(value);

    if (!selectedId || !socketRef.current?.client.connected) return;

    socketRef.current.sendTyping(selectedId, true);

    if (typingTimerRef.current) {
      clearTimeout(typingTimerRef.current);
    }

    typingTimerRef.current = setTimeout(() => {
      socketRef.current?.sendTyping(selectedId, false);
    }, 1200);
  };

  const online = selectedConversation
    ? presence[selectedConversation.otherUser.id] ?? false
    : false;

  return (
    <div className="flex h-dvh overflow-hidden bg-[var(--bg-primary)]">
      <Sidebar />
<main className="flex min-w-0 flex-1 flex-col overflow-hidden p-3 md:p-5">
      
        <div className="mb-4 shrink-0">
          <p className="text-sm font-medium text-[var(--text-secondary)]">
            YOUR CONNECTIONS
          </p>
          <h1 className="mt-1 text-2xl font-bold text-[var(--color-navy)] md:text-3xl">
            Messages
          </h1>
          <p className="mt-1 text-sm text-[var(--text-secondary)]">
            Good conversations create great connections.
          </p>
        </div>

        <div className="grid min-h-0 flex-1 grid-cols-1 overflow-hidden rounded-2xl border border-[#E8D9C8] bg-[var(--bg-secondary)] shadow-[var(--shadow-card)] md:grid-cols-[280px_minmax(0,1fr)]">
          {/* Conversation list */}
          <section
            className={`min-h-0 border-r border-[#E8D9C8] ${
              selectedId ? "hidden md:flex" : "flex"
            } flex-col`}
          >
            <div className="border-b border-[#E8D9C8] p-5">
              <h2 className="mb-4 text-lg font-bold text-[var(--color-navy)]">
                Chats
              </h2>

              <div className="flex items-center gap-2 rounded-xl bg-white px-3 py-2.5">
                <Search size={18} className="text-[var(--text-secondary)]" />
                <input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Search conversations..."
                  className="w-full bg-transparent text-sm outline-none"
                />
              </div>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
              {loadingConversations ? (
                <div className="flex justify-center p-8">
                  <LoaderCircle className="animate-spin text-[var(--color-terracotta)]" />
                </div>
              ) : filteredConversations.length > 0 ? (
                filteredConversations.map((conversation) => {
                  const isOnline =
                    presence[conversation.otherUser.id] ?? false;

                  return (
                    <button
                      key={conversation.id}
                      onClick={() => handleSelectConversation(conversation.id)}
                      className={`flex w-full items-center gap-3 border-b border-[#E8D9C8] p-4 text-left transition hover:bg-[#E9D8C5] ${
                        selectedId === conversation.id ? "bg-[#E9D8C5]" : ""
                      }`}
                    >
                      <div className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[var(--color-navy)] font-semibold text-white">
                        {getInitials(conversation.otherUser.fullName)}
                        {isOnline && (
                          <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-[var(--bg-secondary)] bg-green-500" />
                        )}
                      </div>

                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-2">
                          <p className="truncate font-semibold text-[var(--color-navy)]">
                            {conversation.otherUser.fullName}
                          </p>
                          <span className="shrink-0 text-xs text-[var(--text-secondary)]">
                            {formatLastTime(conversation.lastMessageAt)}
                          </span>
                        </div>

                        <div className="mt-1 flex items-center justify-between gap-2">
                          <p className="truncate text-sm text-[var(--text-secondary)]">
                            {conversation.lastMessagePreview || "Start a conversation"}
                          </p>

                          {conversation.unreadCount > 0 && (
                            <span className="flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-[var(--color-terracotta)] px-1.5 text-xs font-bold text-white">
                              {conversation.unreadCount}
                            </span>
                          )}
                        </div>
                      </div>
                    </button>
                  );
                })
              ) : (
                <p className="p-5 text-center text-sm text-[var(--text-secondary)]">
                  {search
                    ? "No conversations found."
                    : "No conversations yet. Connect with a friend to start chatting."}
                </p>
              )}
            </div>
          </section>

          {/* Active chat */}
          <section
            className={`min-h-0 flex-col ${
              selectedId ? "flex" : "hidden md:flex"
            }`}
          >
            {selectedConversation ? (
              <>
                <header className="flex shrink-0 items-center justify-between border-b border-[#E8D9C8] p-3 md:p-4">
                  <div className="flex min-w-0 items-center gap-3">
                    <button
                      onClick={() => setSelectedId(null)}
                      className="rounded-lg p-2 hover:bg-[#E9D8C5] md:hidden"
                      aria-label="Back to conversations"
                    >
                      <ArrowLeft size={20} />
                    </button>

                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[var(--color-navy)] font-semibold text-white">
                      {getInitials(selectedConversation.otherUser.fullName)}
                    </div>

                    <div className="min-w-0">
                      <h3 className="truncate font-bold text-[var(--color-navy)]">
                        {selectedConversation.otherUser.fullName}
                      </h3>
                      <p className="text-xs text-[var(--text-secondary)]">
                        {typingUserId
                          ? "Typing..."
                          : online
                            ? "● Online"
                            : "Offline"}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-1 text-[var(--color-navy)]">
                    <button
                      onClick={() => void callControls.startCall(selectedConversation.otherUser.id, "VOICE")}
                      disabled={!callSocketConnected || !!callControls.call || !!callControls.incoming}
                      className="rounded-lg p-2 hover:bg-[#E9D8C5] disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label="Voice call"
                      title={callSocketConnected ? "Start voice call" : "Connecting call service..."}
                    >
                      <Phone size={19} />
                    </button>
                    <button
                      onClick={() => void callControls.startCall(selectedConversation.otherUser.id, "VIDEO")}
                      disabled={!callSocketConnected || !!callControls.call || !!callControls.incoming}
                      className="rounded-lg p-2 hover:bg-[#E9D8C5] disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label="Video call"
                      title={callSocketConnected ? "Start video call" : "Connecting call service..."}
                    >
                      <Video size={20} />
                    </button>
                    <button
                      className="rounded-lg p-2 hover:bg-[#E9D8C5]"
                      aria-label="More options"
                    >
                      <MoreVertical size={20} />
                    </button>
                  </div>
                </header>

                {error && (
                  <p className="border-b border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
                    {error}
                  </p>
                )}

                <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-4 md:p-6">
                  {loadingMessages ? (
                    <div className="flex flex-1 items-center justify-center">
                      <LoaderCircle className="animate-spin text-[var(--color-terracotta)]" />
                    </div>
                  ) : messages.length > 0 ? (
                    messages.map((message) => {
                      const sentByMe = message.senderId === currentUserId;

                      return (
                        <div
                          key={message.id}
                          className={`flex ${
                            sentByMe ? "justify-end" : "justify-start"
                          }`}
                        >
                          <div
                            className={`max-w-[80%] rounded-2xl px-4 py-3 md:max-w-[65%] ${
                              sentByMe
                                ? "rounded-br-sm bg-[var(--color-navy)] text-white"
                                : "rounded-bl-sm bg-white text-[var(--color-navy)]"
                            }`}
                          >
                            {message.unsent ? (
                              <p className="text-sm italic opacity-70">
                                This message was unsent.
                              </p>
                            ) : message.type === "IMAGE" ? (
                              <p className="text-sm">📷 Image message</p>
                            ) : (
                              <p className="break-words text-sm leading-relaxed">
                                {message.text}
                              </p>
                            )}

                            <div
                              className={`mt-2 flex items-center justify-end gap-1 text-[10px] ${
                                sentByMe
                                  ? "text-white/60"
                                  : "text-[var(--text-secondary)]"
                              }`}
                            >
                              {formatTime(message.createdAt)}
                              {sentByMe && <CheckCheck size={13} />}
                              {sentByMe && message.status === "READ" && (
                                <span>Read</span>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })
                  ) : (
                    <div className="flex flex-1 items-center justify-center text-center text-sm text-[var(--text-secondary)]">
                      No messages yet. Say hello!
                    </div>
                  )}

                  <div ref={messageEndRef} />
                </div>

                <form
                  onSubmit={handleSendMessage}
                  className="flex shrink-0 items-center gap-2 border-t border-[#E8D9C8] bg-[var(--bg-secondary)] p-3 md:gap-3 md:p-4"
                >
                  <button
                    type="button"
                    disabled
                    className="hidden rounded-lg p-2 text-[var(--text-secondary)] opacity-50 sm:block"
                    aria-label="Attach file"
                    title="Image messaging will be connected next"
                  >
                    <Paperclip size={20} />
                  </button>

                  <input
                    value={messageText}
                    onChange={(event) => handleTyping(event.target.value)}
                    placeholder="Type your message..."
                    maxLength={4000}
                    className="min-w-0 flex-1 rounded-xl bg-white px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-[var(--color-peach)]"
                  />

                  <button
                    type="button"
                    disabled
                    className="hidden rounded-lg p-2 text-[var(--text-secondary)] opacity-50 sm:block"
                    aria-label="Emoji"
                  >
                    <Smile size={20} />
                  </button>

                  <button
                    type="submit"
                    disabled={!messageText.trim() || sending || !socketConnected}
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[var(--color-terracotta)] text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
                    aria-label="Send message"
                  >
                    {sending ? (
                      <LoaderCircle size={18} className="animate-spin" />
                    ) : (
                      <Send size={18} />
                    )}
                  </button>
                </form>
              </>
            ) : (
              <div className="flex flex-1 flex-col items-center justify-center p-6 text-center">
                <div className="mb-4 flex h-20 w-20 items-center justify-center rounded-full bg-[#E9D8C5] text-3xl">
                  💬
                </div>
                <h2 className="text-xl font-bold text-[var(--color-navy)]">
                  Your conversations start here
                </h2>
                <p className="mt-2 max-w-sm text-sm text-[var(--text-secondary)]">
                  Choose someone from your chats and start a conversation.
                </p>
              </div>
            )}
          </section>
        </div>
      </main>
      <CallModal
        call={callControls.call}
        incoming={callControls.incoming}
        phase={callControls.phase}
        error={callControls.error}
        localStream={callControls.localStream}
        remoteStream={callControls.remoteStream}
        muted={callControls.muted}
        cameraOff={callControls.cameraOff}
        currentUserId={currentUserId}
        onAccept={(incomingCall) => void callControls.acceptCall(incomingCall)}
        onReject={(incomingCall) => void callControls.rejectCall(incomingCall)}
        onHangUp={() => void callControls.hangUp()}
        onToggleMute={callControls.toggleMute}
        onToggleCamera={callControls.toggleCamera}
        onCloseError={callControls.clearError}
      />
    </div>
  );
}

export default MessagesPage;
