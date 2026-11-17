
import { useState } from "react";
import {
  Heart,
  MessageCircle,
  UserPlus,
  Users,
  AtSign,
  Check,
  CheckCheck,
  Trash2,
  Bell,
} from "lucide-react";
import Sidebar from "../components/dashboard/Sidebar";

type NotificationType =
  | "like"
  | "comment"
  | "connection"
  | "community"
  | "mention";

type Notification = {
  id: number;
  type: NotificationType;
  name: string;
  avatar: string;
  message: string;
  time: string;
  read: boolean;
};

const initialNotifications: Notification[] = [
  {
    id: 1,
    type: "like",
    name: "Ananya Sharma",
    avatar: "AS",
    message: "liked your recent post.",
    time: "2 minutes ago",
    read: false,
  },
  {
    id: 2,
    type: "connection",
    name: "Rahul Verma",
    avatar: "RV",
    message: "sent you a connection request.",
    time: "15 minutes ago",
    read: false,
  },
  {
    id: 3,
    type: "comment",
    name: "Priya Das",
    avatar: "PD",
    message: 'commented on your post: "This is so inspiring!"',
    time: "1 hour ago",
    read: false,
  },
  {
    id: 4,
    type: "community",
    name: "Creative Minds",
    avatar: "CM",
    message: "invited you to join their community.",
    time: "3 hours ago",
    read: true,
  },
  {
    id: 5,
    type: "mention",
    name: "Arjun Mehta",
    avatar: "AM",
    message: "mentioned you in a discussion.",
    time: "Yesterday",
    read: true,
  },
  {
    id: 6,
    type: "like",
    name: "Sneha Roy",
    avatar: "SR",
    message: "liked your recent post.",
    time: "Yesterday",
    read: true,
  },
  {
    id: 7,
    type: "connection",
    name: "Kabir Sen",
    avatar: "KS",
    message: "started following your profile.",
    time: "2 days ago",
    read: true,
  },
];

const notificationStyles: Record<
  NotificationType,
  { icon: typeof Heart; color: string; background: string }
> = {
  like: {
    icon: Heart,
    color: "text-rose-600",
    background: "bg-rose-100",
  },
  comment: {
    icon: MessageCircle,
    color: "text-blue-600",
    background: "bg-blue-100",
  },
  connection: {
    icon: UserPlus,
    color: "text-green-600",
    background: "bg-green-100",
  },
  community: {
    icon: Users,
    color: "text-purple-600",
    background: "bg-purple-100",
  },
  mention: {
    icon: AtSign,
    color: "text-orange-600",
    background: "bg-orange-100",
  },
};

function NotificationsPage() {
  const [notifications, setNotifications] = useState(initialNotifications);
  const [activeFilter, setActiveFilter] = useState("All");

  const unreadCount = notifications.filter(
    (notification) => !notification.read
  ).length;

  const filters = ["All", "Unread", "Likes", "Comments", "Connections"];

  const filteredNotifications = notifications.filter((notification) => {
    if (activeFilter === "Unread") return !notification.read;
    if (activeFilter === "Likes") return notification.type === "like";
    if (activeFilter === "Comments") return notification.type === "comment";
    if (activeFilter === "Connections")
      return notification.type === "connection";

    return true;
  });

  const markAsRead = (id: number) => {
    setNotifications((previous) =>
      previous.map((notification) =>
        notification.id === id
          ? { ...notification, read: true }
          : notification
      )
    );
  };

  const markAllAsRead = () => {
    setNotifications((previous) =>
      previous.map((notification) => ({ ...notification, read: true }))
    );
  };

  const deleteNotification = (id: number) => {
    setNotifications((previous) =>
      previous.filter((notification) => notification.id !== id)
    );
  };

  const clearAll = () => {
    setNotifications([]);
  };

  return (
    <div className="flex h-dvh overflow-hidden bg-[var(--bg-primary)]">
      <Sidebar />

      <main className="min-w-0 flex-1 overflow-y-auto p-4 md:p-8 lg:p-8">
        {/* Page heading */}
        <div className="mb-7 flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-sm font-medium text-[var(--text-secondary)]">
              STAY IN THE LOOP
            </p>
            <h1 className="mt-1 text-3xl font-bold text-[var(--color-navy)]">
              Notifications
            </h1>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">
              All the little moments happening in your circle.
            </p>
          </div>

          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[var(--color-navy)] text-white">
            <Bell size={23} />
          </div>
        </div>

        {/* Notification summary */}
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-[var(--bg-secondary)] p-5 shadow-[var(--shadow-card)]">
          <div>
            <p className="text-sm text-[var(--text-secondary)]">
              Unread notifications
            </p>
            <h2 className="mt-1 text-3xl font-bold text-[var(--color-navy)]">
              {unreadCount}
            </h2>
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              onClick={markAllAsRead}
              disabled={unreadCount === 0}
              className="flex items-center gap-2 rounded-xl border border-[#D3BBA3] px-3 py-2 text-sm font-medium text-[var(--color-navy)] hover:bg-[#E9D8C5] disabled:opacity-40"
            >
              <CheckCheck size={16} />
              Mark all read
            </button>

            <button
              onClick={clearAll}
              disabled={notifications.length === 0}
              className="flex items-center gap-2 rounded-xl bg-[var(--color-terracotta)] px-3 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-40"
            >
              <Trash2 size={16} />
              Clear all
            </button>
          </div>
        </div>

        {/* Filters */}
        <div className="mb-5 flex gap-2 overflow-x-auto pb-2">
          {filters.map((filter) => (
            <button
              key={filter}
              onClick={() => setActiveFilter(filter)}
              className={`shrink-0 rounded-full px-4 py-2 text-sm font-medium transition ${
                activeFilter === filter
                  ? "bg-[var(--color-navy)] text-white"
                  : "bg-[var(--bg-secondary)] text-[var(--text-secondary)] hover:bg-[#E9D8C5]"
              }`}
            >
              {filter}
            </button>
          ))}
        </div>

        {/* Notification list */}
        <section className="overflow-hidden rounded-2xl bg-[var(--bg-secondary)] shadow-[var(--shadow-card)]">
          <div className="border-b border-[#E8D9C8] p-5">
            <h2 className="font-bold text-[var(--color-navy)]">
              Recent activity
            </h2>
          </div>

          {filteredNotifications.length > 0 ? (
            <div>
              {filteredNotifications.map((notification) => {
                const style = notificationStyles[notification.type];
                const Icon = style.icon;

                return (
                  <article
                    key={notification.id}
                    className={`flex items-start gap-3 border-b border-[#E8D9C8] p-4 transition hover:bg-[#E9D8C5] md:gap-4 md:p-5 ${
                      !notification.read ? "bg-[#F8E9DA]" : ""
                    }`}
                  >
                    <div className="relative shrink-0">
                      <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[var(--color-navy)] text-sm font-bold text-white">
                        {notification.avatar}
                      </div>

                      <div
                        className={`absolute -bottom-1 -right-1 flex h-6 w-6 items-center justify-center rounded-full ${style.background}`}
                      >
                        <Icon size={13} className={style.color} />
                      </div>
                    </div>

                    <div className="min-w-0 flex-1">
                      <p className="text-sm leading-relaxed text-[var(--color-navy)]">
                        <span className="font-bold">{notification.name}</span>{" "}
                        {notification.message}
                      </p>

                      <p className="mt-1 text-xs text-[var(--text-secondary)]">
                        {notification.time}
                      </p>
                    </div>

                    <div className="flex shrink-0 items-center gap-1">
                      {!notification.read && (
                        <button
                          onClick={() => markAsRead(notification.id)}
                          title="Mark as read"
                          aria-label="Mark notification as read"
                          className="rounded-lg p-2 text-[var(--color-navy)] hover:bg-[#D3BBA3]"
                        >
                          <Check size={17} />
                        </button>
                      )}

                      <button
                        onClick={() => deleteNotification(notification.id)}
                        title="Delete notification"
                        aria-label="Delete notification"
                        className="rounded-lg p-2 text-[var(--text-secondary)] hover:bg-[#D3BBA3] hover:text-red-600"
                      >
                        <Trash2 size={16} />
                      </button>

                      {!notification.read && (
                        <span className="h-2 w-2 rounded-full bg-[var(--color-terracotta)]" />
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="flex flex-col items-center px-5 py-16 text-center">
              <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-[#E9D8C5]">
                <Bell size={28} className="text-[var(--color-navy)]" />
              </div>
              <h3 className="font-bold text-[var(--color-navy)]">
                You're all caught up!
              </h3>
              <p className="mt-2 text-sm text-[var(--text-secondary)]">
                Nothing to see here right now. Your next update will appear
                here.
              </p>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

export default NotificationsPage;
