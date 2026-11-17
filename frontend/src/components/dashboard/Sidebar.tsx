import { Link, useLocation, useNavigate } from "react-router-dom";
import { authApi } from "../../api/authApi";
import { tokenStorage } from "../../api/tokenStorage";
import useAuthStore from "../../store/authStore";
import { UserRound } from "lucide-react";
import { Globe } from "lucide-react";
import {
  Home,
  Compass,
  Users,
  MessageCircle,
  Bell,
  Settings,
  PlusCircle,
  LogOut,
  Sparkles,
} from "lucide-react";

const menuItems = [
  { name: "Home", icon: Home, path: "/dashboard" },
  { name: "Explore", icon: Compass, path: "/explore" },
  { name: "My Network", icon: Users, path: "/network" },
  { name: "Messages", icon: MessageCircle, path: "/messages" },
  { name: "Notifications", icon: Bell, path: "/notifications" },
  { name: "My Profile", icon: UserRound, path: "/profile" },
  { name: "Communities", icon: Globe, path: "/communities" },
];


export default function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();

  const clearSession = useAuthStore((state) => state.clearSession);

  const handleLogout = async () => {
    const refreshToken = tokenStorage.getRefreshToken();

    try {
      if (refreshToken) {
        await authApi.logout(refreshToken);
      }
    } catch (error) {
      console.error("Logout API failed:", error);
    } finally {
      clearSession();
      navigate("/", { replace: true });
    }
  };


  return (
    <aside className="hidden lg:flex flex-col w-64 min-h-screen bg-[#0F1B36] text-white p-6 sticky top-0">

      <Link to="/" className="text-3xl font-bold mb-12">
        Vibe<span className="text-[#D87355]">Circle.</span>
      </Link>

      <p className="text-xs uppercase tracking-widest text-white/40 mb-5">
        Menu
      </p>

      <nav className="space-y-2">
        {menuItems.map((item) => {
          const Icon = item.icon;
          const active = location.pathname === item.path;

          return (
            <Link
              key={item.name}
              to={item.path}
              className={`flex items-center gap-4 px-4 py-3 rounded-xl transition ${
                active
                  ? "bg-[#D87355] text-white"
                  : "text-white/65 hover:bg-white/10 hover:text-white"
              }`}
            >
              <Icon size={20} />
              <span className="font-medium">{item.name}</span>
            </Link>
          );
        })}
      </nav>

      <div className="mt-10">
        <p className="text-xs uppercase tracking-widest text-white/40 mb-5">
          Your Space
        </p>

        <button className="flex items-center gap-4 px-4 py-3 text-white/65 hover:text-white transition">
          <PlusCircle size={20} />
          Create Community
        </button>

        <button className="flex items-center gap-4 px-4 py-3 text-white/65 hover:text-white transition">
          <Sparkles size={20} />
          Saved Posts
        </button>
      </div>

      <div className="mt-auto pt-8 border-t border-white/10 space-y-3">
        <button className="flex items-center gap-4 px-4 py-2 text-white/60 hover:text-white">
          <Settings size={20} />
          Settings
        </button>

        <button
  type="button"
  onClick={handleLogout}
  className="flex w-full items-center gap-4 px-4 py-2 text-white/60 hover:text-[#E9A58D] transition"
>
  <LogOut size={20} />
  Log out
</button>
      </div>
    </aside>
  );
}
