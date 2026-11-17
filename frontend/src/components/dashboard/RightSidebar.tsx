
import { TrendingUp, Users, ArrowUpRight } from "lucide-react";

const friends = [
  { name: "Ananya", initials: "A", color: "bg-[#8C6B85]" },
  { name: "Rahul", initials: "R", color: "bg-[#507C73]" },
  { name: "Priya", initials: "P", color: "bg-[#C18459]" },
  { name: "Sneha", initials: "S", color: "bg-[#A66C61]" },
];

const communities = [
  { name: "Creative Minds", members: "2.4k members", emoji: "🎨" },
  { name: "Tech Explorers", members: "1.8k members", emoji: "💻" },
  { name: "Book Lovers", members: "950 members", emoji: "📚" },
];

export default function RightSidebar() {
  return (
    <aside className="hidden xl:block w-80 shrink-0 pl-5">

      {/* PROFILE */}
      <div className="bg-[#0F1B36] text-white rounded-2xl p-5">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-full bg-[#D87355] flex items-center justify-center font-bold text-lg">
            R
          </div>

          <div>
            <h3 className="font-bold">Welcome back!</h3>
            <p className="text-white/60 text-sm">Your circle awaits.</p>
          </div>
        </div>

        <div className="border-t border-white/10 mt-5 pt-4 flex justify-between text-center">
          <div>
            <p className="font-bold text-xl">12</p>
            <p className="text-xs text-white/50">Friends</p>
          </div>
          <div>
            <p className="font-bold text-xl">5</p>
            <p className="text-xs text-white/50">Groups</p>
          </div>
          <div>
            <p className="font-bold text-xl">8</p>
            <p className="text-xs text-white/50">Posts</p>
          </div>
        </div>
      </div>

      {/* FRIENDS */}
      <section className="mt-8">
        <div className="flex justify-between items-center mb-5">
          <h3 className="font-bold text-lg">Your Circle</h3>
          <Users size={19} className="text-[#D87355]" />
        </div>

        <div className="space-y-4">
          {friends.map((friend) => (
            <div key={friend.name} className="flex items-center gap-3">
              <div className={`w-10 h-10 rounded-full ${friend.color} text-white flex items-center justify-center font-bold`}>
                {friend.initials}
              </div>

              <div className="flex-1">
                <p className="font-semibold text-sm">{friend.name}</p>
                <p className="text-xs text-[#927B68]">Online now</p>
              </div>

              <span className="w-2.5 h-2.5 rounded-full bg-green-500" />
            </div>
          ))}
        </div>
      </section>

      {/* COMMUNITIES */}
      <section className="mt-9">
        <div className="flex justify-between items-center mb-5">
          <h3 className="font-bold text-lg">Communities</h3>
          <ArrowUpRight size={19} className="text-[#D87355]" />
        </div>

        <div className="space-y-3">
          {communities.map((community) => (
            <div
              key={community.name}
              className="bg-[#F5EBDD] rounded-xl p-4 flex items-center gap-3 hover:bg-[#E9D8C5] transition cursor-pointer"
            >
              <span className="text-2xl">{community.emoji}</span>

              <div>
                <p className="font-semibold text-sm">{community.name}</p>
                <p className="text-xs text-[#927B68]">{community.members}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* TRENDING */}
      <section className="mt-9 bg-[#F5EBDD] p-5 rounded-2xl">
        <div className="flex items-center gap-2 mb-4">
          <TrendingUp size={19} className="text-[#D87355]" />
          <h3 className="font-bold">Trending interests</h3>
        </div>

        {["#CreativeCoding", "#Photography", "#BookCommunity"].map((tag) => (
          <p key={tag} className="text-sm font-semibold text-[#927B68] py-2">
            {tag}
          </p>
        ))}
      </section>

    </aside>
  );
}
