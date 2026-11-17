import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Search,
  Users,
  UserPlus,
  UserX,
  Check,
  X,
  Clock,
  MessageCircle,
  Sparkles,
  MapPin,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { AxiosError } from "axios";
import { useNavigate } from "react-router-dom";

import Sidebar from "../components/dashboard/Sidebar";
import {
  friendshipApi,
  type Friend,
  type FriendRequest,
} from "../api/friendshipApi";
import {
  discoveryApi,
  type DiscoveredUser,
} from "../api/discoveryApi";

type Tab = "suggestions" | "requests" | "sent" | "friends";

type NetworkUser = {
  id: number;
  name: string;
  username: string;
  initials: string;
  color: string;
  requestId?: number;
};

const avatarColors = [
  "bg-[#8C6B85]",
  "bg-[#507C73]",
  "bg-[#C18459]",
  "bg-[#637A9B]",
  "bg-[#A66C61]",
  "bg-[#927B68]",
];

function getInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase() || "?";
}

function toNetworkUser(
  id: number,
  name: string,
  username: string,
  index: number,
  requestId?: number
): NetworkUser {
  return {
    id,
    name,
    username,
    initials: getInitials(name),
    color: avatarColors[index % avatarColors.length],
    requestId,
  };
}

function getErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as
      | { message?: string; detail?: string; title?: string }
      | undefined;

    return (
      data?.message ||
      data?.detail ||
      data?.title ||
      error.message ||
      "Something went wrong. Please try again."
    );
  }

  return error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
}

function getDiscoveryError(error: unknown): string {
  if (error instanceof AxiosError && error.response?.status === 409) {
    return (
      getErrorMessage(error) ||
      "People could not be loaded. Please try again."
    );
  }

  return getErrorMessage(error);
}

export default function NetworkPage() {
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<Tab>("suggestions");
  const [search, setSearch] = useState("");

  const [friends, setFriends] = useState<NetworkUser[]>([]);
  const [requests, setRequests] = useState<NetworkUser[]>([]);
  const [sentRequests, setSentRequests] = useState<NetworkUser[]>([]);
  const [suggestions, setSuggestions] = useState<DiscoveredUser[]>([]);

  const [loading, setLoading] = useState(true);
  const [discoveryLoading, setDiscoveryLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [discoveryError, setDiscoveryError] = useState("");
  const [message, setMessage] = useState("");

  const [discoveryPage, setDiscoveryPage] = useState(0);
  const [hasNextDiscoveryPage, setHasNextDiscoveryPage] = useState(false);

  const tabs = [
    { id: "suggestions" as Tab, label: "Discover", icon: Sparkles },
    { id: "requests" as Tab, label: "Requests", icon: UserPlus },
    { id: "sent" as Tab, label: "Sent", icon: Clock },
    { id: "friends" as Tab, label: "My Friends", icon: Users },
  ];

  const loadNetwork = useCallback(async () => {
    setError("");

    try {
      const [friendsPage, receivedPage, sentPage] = await Promise.all([
        friendshipApi.getFriends(),
        friendshipApi.getReceivedRequests(),
        friendshipApi.getSentRequests(),
      ]);

      setFriends(
        friendsPage.items.map((friend: Friend, index: number) =>
          toNetworkUser(friend.userId, friend.fullName, friend.username, index)
        )
      );

      setRequests(
        receivedPage.items.map((request: FriendRequest, index: number) =>
          toNetworkUser(
            request.otherUserId,
            request.otherFullName,
            request.otherUsername,
            index,
            request.id
          )
        )
      );

      setSentRequests(
        sentPage.items.map((request: FriendRequest, index: number) =>
          toNetworkUser(
            request.otherUserId,
            request.otherFullName,
            request.otherUsername,
            index,
            request.id
          )
        )
      );
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }, []);

  const loadDiscoveries = useCallback(
    async (page = 0, query = "") => {
      setDiscoveryLoading(true);
      setDiscoveryError("");

      try {
        const result = await discoveryApi.getPeople({
          page,
          size: 20,
          search: query.trim() || undefined,
        });

        setSuggestions(result.items);
        setDiscoveryPage(result.page);
        setHasNextDiscoveryPage(result.hasNext);
      } catch (err) {
        setSuggestions([]);
        setHasNextDiscoveryPage(false);
        setDiscoveryError(getDiscoveryError(err));
      } finally {
        setDiscoveryLoading(false);
      }
    },
    []
  );

  const refreshAll = useCallback(async () => {
    setLoading(true);
    await loadNetwork();
    setLoading(false);
  }, [loadNetwork]);

  useEffect(() => {
    void refreshAll();
  }, [refreshAll]);

  useEffect(() => {
    if (activeTab !== "suggestions") return;

    const timeout = setTimeout(() => {
      void loadDiscoveries(0, search);
    }, 350);

    return () => clearTimeout(timeout);
  }, [search, activeTab, loadDiscoveries]);

  const currentList =
    activeTab === "requests"
      ? requests
      : activeTab === "sent"
        ? sentRequests
        : activeTab === "friends"
          ? friends
          : [];

  const filteredUsers = useMemo(() => {
    const query = search.toLowerCase().trim();

    if (!query) return currentList;

    return currentList.filter((user) =>
      [user.name, user.username].some((value) =>
        value.toLowerCase().includes(query)
      )
    );
  }, [currentList, search]);

  const filteredSuggestions = suggestions;

  async function sendRequest(person: DiscoveredUser) {
    setActionLoading(person.userId);
    setError("");
    setMessage("");

    try {
      const result = await friendshipApi.sendRequest(person.userId);

      if (result.outcome === "FRIENDS") {
        setMessage(`You and ${person.fullName} are now connected!`);
      } else {
        setMessage(`Connection request sent to ${person.fullName}.`);
      }

      await Promise.all([
        loadNetwork(),
        loadDiscoveries(discoveryPage, search),
      ]);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setActionLoading(null);
    }
  }

  async function acceptRequest(user: NetworkUser) {
    if (user.requestId == null) return;

    setActionLoading(user.id);
    setError("");
    setMessage("");

    try {
      await friendshipApi.acceptRequest(user.requestId);
      setMessage(`You and ${user.name} are now connected!`);
      await loadNetwork();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setActionLoading(null);
    }
  }

  async function rejectRequest(user: NetworkUser) {
    if (user.requestId == null) return;

    setActionLoading(user.id);
    setError("");
    setMessage("");

    try {
      await friendshipApi.rejectRequest(user.requestId);
      setMessage(`Request from ${user.name} declined.`);
      await loadNetwork();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setActionLoading(null);
    }
  }

  async function cancelRequest(user: NetworkUser) {
    if (user.requestId == null) return;

    setActionLoading(user.id);
    setError("");
    setMessage("");

    try {
      await friendshipApi.cancelRequest(user.requestId);
      setMessage("Connection request cancelled.");
      await loadNetwork();
      await loadDiscoveries(discoveryPage, search);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setActionLoading(null);
    }
  }

  async function removeFriend(user: NetworkUser) {
    const confirmed = window.confirm(
      `Are you sure you want to remove ${user.name} from your friends?`
    );

    if (!confirmed) return;

    setActionLoading(user.id);
    setError("");
    setMessage("");

    try {
      await friendshipApi.removeFriend(user.id);
      setMessage(`${user.name} removed from your circle.`);
      await loadNetwork();
      await loadDiscoveries(discoveryPage, search);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setActionLoading(null);
    }
  }

  async function changeDiscoveryPage(nextPage: number) {
    if (nextPage < 0) return;
    await loadDiscoveries(nextPage, search);
  }

  return (
    <div className="min-h-screen bg-[#D3BBA3] text-[#0F1B36] flex">
      <Sidebar />

      <main className="flex-1 min-w-0 px-5 md:px-10 py-7 overflow-y-auto">
        <header className="mb-9">
          <p className="text-[#D87355] uppercase tracking-widest text-xs font-bold">
            Grow your circle
          </p>
          <h1 className="text-3xl md:text-4xl font-bold mt-2">
            Your Network. 🤎
          </h1>
          <p className="text-[#927B68] mt-2">
            Find your people and build meaningful connections.
          </p>
        </header>

        <section className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-9">
          {[
            { label: "Friends", value: friends.length, icon: Users },
            { label: "Requests", value: requests.length, icon: UserPlus },
            { label: "Sent", value: sentRequests.length, icon: Clock },
            { label: "Discover", value: suggestions.length, icon: Sparkles },
          ].map((stat) => {
            const Icon = stat.icon;

            return (
              <div
                key={stat.label}
                className="bg-[#F5EBDD] rounded-2xl p-5 shadow-sm"
              >
                <Icon className="text-[#D87355]" size={23} />
                <h2 className="text-2xl font-bold mt-4">{stat.value}</h2>
                <p className="text-sm text-[#927B68] mt-1">{stat.label}</p>
              </div>
            );
          })}
        </section>

        {message && (
          <div
            role="status"
            className="bg-[#0F1B36] text-white p-4 rounded-xl mb-5 flex justify-between items-center"
          >
            <p className="text-sm">{message}</p>
            <button
              type="button"
              onClick={() => setMessage("")}
              aria-label="Dismiss message"
            >
              <X size={18} />
            </button>
          </div>
        )}

        {error && (
          <div
            role="alert"
            className="bg-red-100 text-red-700 p-4 rounded-xl mb-5 flex justify-between items-center gap-4"
          >
            <p className="text-sm">{error}</p>
            <button
              type="button"
              onClick={() => void loadNetwork()}
              className="font-semibold underline shrink-0"
            >
              Retry
            </button>
          </div>
        )}

        <div className="flex gap-3 overflow-x-auto pb-3 mb-6">
          {tabs.map((tab) => {
            const Icon = tab.icon;

            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => {
                  setActiveTab(tab.id);
                  setSearch("");
                  setMessage("");
                }}
                className={`flex items-center gap-2 px-5 py-3 rounded-full whitespace-nowrap font-semibold text-sm transition ${
                  activeTab === tab.id
                    ? "bg-[#0F1B36] text-white"
                    : "bg-[#F5EBDD] text-[#927B68] hover:bg-[#E9D8C5]"
                }`}
              >
                <Icon size={17} />
                {tab.label}
                {tab.id === "requests" && requests.length > 0 && (
                  <span className="bg-[#D87355] text-white rounded-full px-2 py-0.5 text-xs">
                    {requests.length}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="relative mb-8 max-w-xl">
          <Search
            size={20}
            className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
          />
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search people by name or username..."
            className="w-full bg-[#F5EBDD] rounded-xl py-4 pl-12 pr-5 outline-none border border-transparent focus:border-[#D87355]"
          />
        </div>

        <div className="flex justify-between items-center mb-6 gap-4">
          <div>
            <h2 className="text-2xl font-bold">
              {activeTab === "suggestions" && "People you may like"}
              {activeTab === "requests" && "Incoming requests"}
              {activeTab === "sent" && "Requests you've sent"}
              {activeTab === "friends" && "Your people"}
            </h2>
            <p className="text-sm text-[#927B68] mt-1">
              {activeTab === "suggestions"
                ? `${filteredSuggestions.length} people suggested for you`
                : `${filteredUsers.length} people found`}
            </p>
          </div>

          <button
            type="button"
            onClick={() =>
              activeTab === "suggestions"
                ? void loadDiscoveries(discoveryPage, search)
                : void loadNetwork()
            }
            className="text-sm font-semibold text-[#D87355] flex items-center gap-2 shrink-0"
          >
            <RefreshCw size={15} />
            Refresh
          </button>
        </div>

        {loading ? (
          <div className="bg-[#F5EBDD] rounded-2xl p-12 text-center text-[#927B68]">
            Loading your network...
          </div>
        ) : activeTab === "suggestions" ? (
          discoveryLoading ? (
            <div className="bg-[#F5EBDD] rounded-2xl p-12 text-center text-[#927B68]">
              Finding people who share your vibe...
            </div>
          ) : discoveryError ? (
            <div className="bg-[#F5EBDD] rounded-2xl p-8 md:p-12 text-center">
              <Sparkles size={38} className="mx-auto text-[#D87355]" />
              <h3 className="text-xl font-bold mt-5">
                We couldn't load people right now
              </h3>
              <p className="text-[#927B68] mt-2 max-w-xl mx-auto">
                {discoveryError}
              </p>
              <button
                type="button"
                onClick={() => void loadDiscoveries(0, search)}
                className="mt-6 bg-[#0F1B36] text-white px-6 py-3 rounded-xl font-semibold"
              >
                Try again
              </button>
            </div>
          ) : filteredSuggestions.length > 0 ? (
            <>
              <section className="grid sm:grid-cols-2 xl:grid-cols-3 gap-5">
                {filteredSuggestions.map((person, index) => {
                  const initials = getInitials(person.fullName);
                  const color = avatarColors[index % avatarColors.length];

                  return (
                    <article
                      key={person.userId}
                      className="bg-[#F5EBDD] rounded-2xl p-6 shadow-sm hover:-translate-y-1 hover:shadow-lg transition-all"
                    >
                      <div className="flex justify-between items-start gap-3">
                        <div
                          className={`w-16 h-16 rounded-full ${color} flex items-center justify-center text-white text-2xl font-bold shrink-0`}
                        >
                          {initials}
                        </div>
                        <span className="text-xs bg-[#E9D8C5] text-[#927B68] px-3 py-2 rounded-full text-right">
                          {person.similarityPercent}% match
                        </span>
                      </div>

                      <div className="mt-5">
                        <h3 className="font-bold text-xl">
                          {person.fullName}
                        </h3>
                        <p className="text-sm text-[#927B68] mt-1">
                          @{person.username}
                        </p>

                        {person.bio && (
                          <p className="text-sm text-[#594A40] mt-4 leading-relaxed min-h-10">
                            {person.bio}
                          </p>
                        )}

                        {person.city?.name && (
                          <p className="flex items-center gap-2 mt-4 text-xs text-[#927B68]">
                            <MapPin size={14} />
                            {person.city.name}
                          </p>
                        )}

                        {person.sharedInterests.length > 0 && (
                          <div className="flex flex-wrap gap-2 mt-4">
                            {person.sharedInterests.slice(0, 4).map((interest) => (
                              <span
                                key={interest}
                                className="text-xs bg-[#E9D8C5] text-[#594A40] px-3 py-1.5 rounded-full"
                              >
                                {interest}
                              </span>
                            ))}
                          </div>
                        )}
                        <p className="text-xs text-[#927B68] mt-3">
                          {person.sharedCount} shared interest
                          {person.sharedCount === 1 ? "" : "s"}
                        </p>
                      </div>

                      <button
                        type="button"
                        onClick={() => void sendRequest(person)}
                        disabled={actionLoading === person.userId}
                        className="w-full bg-[#D87355] text-white py-3 rounded-xl font-semibold flex items-center justify-center gap-2 hover:bg-[#bd6047] transition mt-6 disabled:opacity-50"
                      >
                        <UserPlus size={17} />
                        {actionLoading === person.userId
                          ? "Sending..."
                          : "Connect"}
                      </button>
                    </article>
                  );
                })}
              </section>

              <div className="flex items-center justify-center gap-4 mt-8">
                <button
                  type="button"
                  onClick={() => void changeDiscoveryPage(discoveryPage - 1)}
                  disabled={discoveryPage === 0 || discoveryLoading}
                  className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#F5EBDD] disabled:opacity-40"
                >
                  <ChevronLeft size={17} />
                  Previous
                </button>
                <span className="text-sm text-[#594A40]">
                  Page {discoveryPage + 1}
                </span>
                <button
                  type="button"
                  onClick={() => void changeDiscoveryPage(discoveryPage + 1)}
                  disabled={!hasNextDiscoveryPage || discoveryLoading}
                  className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#F5EBDD] disabled:opacity-40"
                >
                  Next
                  <ChevronRight size={17} />
                </button>
              </div>
            </>
          ) : (
            <div className="bg-[#F5EBDD] rounded-2xl p-12 text-center">
              <Sparkles size={38} className="mx-auto text-[#D87355]" />
              <h3 className="text-xl font-bold mt-5">
                No new people to show yet
              </h3>
              <p className="text-[#927B68] mt-2 max-w-lg mx-auto">
                Try again later, or check that your profile and interests are
                set up. People already connected to you or with pending
                requests won't appear here.
              </p>
            </div>
          )
        ) : filteredUsers.length > 0 ? (
          <section className="grid sm:grid-cols-2 xl:grid-cols-3 gap-5">
            {filteredUsers.map((user) => (
              <article
                key={user.id}
                className="bg-[#F5EBDD] rounded-2xl p-6 shadow-sm hover:-translate-y-1 hover:shadow-lg transition-all"
              >
                <div className="flex justify-between items-start">
                  <div
                    className={`w-16 h-16 rounded-full ${user.color} flex items-center justify-center text-white text-2xl font-bold`}
                  >
                    {user.initials}
                  </div>
                  <span className="text-xs bg-[#E9D8C5] text-[#927B68] px-3 py-2 rounded-full">
                    VibeCircle member
                  </span>
                </div>

                <div className="mt-5">
                  <h3 className="font-bold text-xl">{user.name}</h3>
                  <p className="text-sm text-[#927B68] mt-1">
                    @{user.username}
                  </p>
                </div>

                <div className="flex gap-3 mt-6">
                  {activeTab === "requests" && (
                    <>
                      <button
                        type="button"
                        onClick={() => void acceptRequest(user)}
                        disabled={actionLoading === user.id}
                        className="flex-1 bg-[#0F1B36] text-white py-3 rounded-xl font-semibold flex items-center justify-center gap-2 disabled:opacity-50"
                      >
                        <Check size={17} />
                        {actionLoading === user.id ? "Please wait..." : "Accept"}
                      </button>
                      <button
                        type="button"
                        onClick={() => void rejectRequest(user)}
                        disabled={actionLoading === user.id}
                        aria-label={`Decline request from ${user.name}`}
                        className="bg-[#E9D8C5] text-[#D87355] px-4 rounded-xl disabled:opacity-50"
                      >
                        <X size={19} />
                      </button>
                    </>
                  )}

                  {activeTab === "sent" && (
                    <button
                      type="button"
                      onClick={() => void cancelRequest(user)}
                      disabled={actionLoading === user.id}
                      className="flex-1 bg-[#E9D8C5] text-[#0F1B36] py-3 rounded-xl font-semibold flex items-center justify-center gap-2 disabled:opacity-50"
                    >
                      <X size={17} />
                      {actionLoading === user.id
                        ? "Please wait..."
                        : "Cancel Request"}
                    </button>
                  )}

                  {activeTab === "friends" && (
                    <>
                      <button
                        type="button"
                        onClick={() => navigate("/messages")}
                        className="flex-1 bg-[#0F1B36] text-white py-3 rounded-xl font-semibold flex items-center justify-center gap-2"
                      >
                        <MessageCircle size={17} />
                        Message
                      </button>
                      <button
                        type="button"
                        onClick={() => void removeFriend(user)}
                        disabled={actionLoading === user.id}
                        title="Remove friend"
                        className="bg-[#E9D8C5] text-[#D87355] px-4 rounded-xl disabled:opacity-50"
                      >
                        <UserX size={19} />
                      </button>
                    </>
                  )}
                </div>
              </article>
            ))}
          </section>
        ) : (
          <div className="bg-[#F5EBDD] rounded-2xl p-12 text-center">
            <Users size={38} className="mx-auto text-[#D87355]" />
            <h3 className="text-xl font-bold mt-5">
              {activeTab === "requests"
                ? "You're all caught up!"
                : activeTab === "sent"
                  ? "No pending requests"
                  : "Your circle is waiting to grow"}
            </h3>
            <p className="text-[#927B68] mt-2">
              {activeTab === "friends"
                ? "Your accepted connections will appear here."
                : "There is nothing to show right now."}
            </p>
          </div>
        )}
      </main>
    </div>
  );
}
