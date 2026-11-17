import { Search, Bell } from "lucide-react";
import { Link } from "react-router-dom";
import useAuthStore from "../../store/authStore";

export default function Topbar() {
  const user = useAuthStore((state) => state.user);
  const initials = (user?.fullName || user?.username || "U")
    .trim()
    .split(/\\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");

  return (
    <header className="flex items-center justify-between gap-4 py-5 border-b border-[#0F1B36]/10">
      <div className="relative flex-1 max-w-xl">
        <Search
          className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
          size={19}
        />
        <input
          type="search"
          placeholder="Search people, interests, communities..."
          aria-label="Search VibeCircle"
          className="w-full bg-[#F5EBDD] rounded-full py-3 pl-12 pr-5 outline-none border border-transparent focus:border-[#D87355]"
        />
      </div>

      <div className="flex items-center gap-3">
        <Link
          to="/notifications"
          aria-label="Open notifications"
          title="Notifications"
          className="relative p-3 rounded-full bg-[#F5EBDD] hover:bg-[#E9D8C5] transition"
        >
          <Bell size={20} />
          <span className="absolute top-2 right-2 w-2 h-2 bg-[#D87355] rounded-full" />
        </Link>

        <Link
          to="/profile"
          aria-label="Open my profile"
          title="My profile"
          className="w-11 h-11 rounded-full bg-[#0F1B36] text-white flex items-center justify-center font-bold hover:ring-2 hover:ring-[#D87355] transition"
        >
          {initials || "U"}
        </Link>
      </div>
    </header>
  );
}
