
import { useMemo, useState } from "react";
import {
  Search,
  Users,
  Plus,
  Compass,
  ArrowRight,
  Check,
  X,
  Hash,
} from "lucide-react";
import { Link } from "react-router-dom";
import Sidebar from "../components/dashboard/Sidebar";

type Community = {
  id: number;
  name: string;
  category: string;
  description: string;
  members: number;
  emoji: string;
  cover: string;
  privacy: "Public" | "Private";
  joined: boolean;
};

const initialCommunities: Community[] = [
  {
    id: 1,
    name: "Creative Minds",
    category: "Art & Design",
    description: "A home for artists, designers and people who love creating beautiful things.",
    members: 2400,
    emoji: "🎨",
    cover: "from-[#8C6B85] to-[#D87355]",
    privacy: "Public",
    joined: false,
  },
  {
    id: 2,
    name: "Tech Explorers",
    category: "Technology",
    description: "Explore programming, development, AI and everything shaping our digital world.",
    members: 1800,
    emoji: "💻",
    cover: "from-[#0F1B36] to-[#507C73]",
    privacy: "Public",
    joined: true,
  },
  {
    id: 3,
    name: "The Book Corner",
    category: "Books",
    description: "For readers, storytellers and people who always have one more book to read.",
    members: 950,
    emoji: "📚",
    cover: "from-[#C18459] to-[#927B68]",
    privacy: "Public",
    joined: false,
  },
  {
    id: 4,
    name: "Wander Souls",
    category: "Travel",
    description: "Share travel stories, hidden destinations and adventures from around the world.",
    members: 3200,
    emoji: "✈️",
    cover: "from-[#507C73] to-[#0F1B36]",
    privacy: "Public",
    joined: false,
  },
  {
    id: 5,
    name: "Music & More",
    category: "Music",
    description: "Discover artists, share playlists and talk about the music you love.",
    members: 1500,
    emoji: "🎵",
    cover: "from-[#637A9B] to-[#8C6B85]",
    privacy: "Public",
    joined: false,
  },
  {
    id: 6,
    name: "Lens Stories",
    category: "Photography",
    description: "A creative space for photography enthusiasts and visual storytellers.",
    members: 780,
    emoji: "📸",
    cover: "from-[#A66C61] to-[#C18459]",
    privacy: "Public",
    joined: false,
  },
];

const categories = [
  "All",
  "Technology",
  "Art & Design",
  "Books",
  "Travel",
  "Music",
  "Photography",
];

export default function CommunitiesPage() {
  const [communities, setCommunities] = useState(initialCommunities);
  const [activeCategory, setActiveCategory] = useState("All");
  const [search, setSearch] = useState("");
  const [showCreateModal, setShowCreateModal] = useState(false);

  const [newCommunity, setNewCommunity] = useState({
    name: "",
    category: "Technology",
    description: "",
  });

  const filteredCommunities = useMemo(() => {
    const query = search.toLowerCase().trim();

    return communities.filter((community) => {
      const matchesCategory =
        activeCategory === "All" ||
        community.category === activeCategory;

      const matchesSearch =
        community.name.toLowerCase().includes(query) ||
        community.description.toLowerCase().includes(query) ||
        community.category.toLowerCase().includes(query);

      return matchesCategory && matchesSearch;
    });
  }, [communities, activeCategory, search]);

  function toggleJoin(id: number) {
    setCommunities((previous) =>
      previous.map((community) =>
        community.id === id
          ? {
              ...community,
              joined: !community.joined,
              members: community.members + (community.joined ? -1 : 1),
            }
          : community
      )
    );
  }

  function createCommunity() {
    if (
      !newCommunity.name.trim() ||
      !newCommunity.description.trim()
    ) {
      return;
    }

    const community: Community = {
      id: Date.now(),
      name: newCommunity.name.trim(),
      category: newCommunity.category,
      description: newCommunity.description.trim(),
      members: 1,
      emoji: "🌱",
      cover: "from-[#0F1B36] to-[#D87355]",
      privacy: "Public",
      joined: true,
    };

    setCommunities((previous) => [community, ...previous]);
    setNewCommunity({
      name: "",
      category: "Technology",
      description: "",
    });
    setActiveCategory("All");
    setSearch("");
    setShowCreateModal(false);
  }

  return (
    <div className="min-h-screen bg-[#D3BBA3] text-[#0F1B36] flex">
      <Sidebar />

      <main className="flex-1 min-w-0 px-5 md:px-10 py-7">

        {/* HEADER */}
        <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-5 mb-10">
          <div>
            <p className="text-xs uppercase tracking-widest text-[#D87355] font-bold">
              Find your community
            </p>

            <h1 className="text-3xl md:text-4xl font-bold mt-2">
              Your people. Your spaces. 🤎
            </h1>

            <p className="text-[#927B68] mt-2">
              Find communities built around the things you love.
            </p>
          </div>

          <button
            onClick={() => setShowCreateModal(true)}
            className="flex items-center justify-center gap-2 bg-[#0F1B36] text-white px-6 py-3 rounded-xl font-semibold hover:bg-[#24385D] transition"
          >
            <Plus size={19} />
            Create Community
          </button>
        </header>

        {/* HERO BANNER */}
        <section className="bg-[#0F1B36] text-white rounded-3xl p-8 md:p-12 mb-10 relative overflow-hidden">
          <div className="relative z-10 max-w-2xl">
            <span className="text-[#E9A58D] text-sm font-bold tracking-widest">
              BELONG TO SOMETHING
            </span>

            <h2 className="text-3xl md:text-5xl font-bold mt-4 leading-tight">
              Great connections
              <br />
              start with shared interests.
            </h2>

            <p className="text-white/65 mt-5 leading-relaxed">
              Join conversations, exchange ideas and find people
              who make your interests even more exciting.
            </p>

            <button
              onClick={() => {
                document.getElementById("community-list")?.scrollIntoView({
                  behavior: "smooth",
                });
              }}
              className="mt-7 bg-[#D87355] text-white px-6 py-3 rounded-xl font-semibold hover:bg-[#bd6047] transition"
            >
              Explore communities <ArrowRight className="inline ml-2" size={18} />
            </button>
          </div>

          <div className="absolute -right-12 -bottom-32 w-80 h-80 rounded-full border-[45px] border-white/5" />
          <div className="absolute right-24 -top-20 w-60 h-60 rounded-full bg-[#D87355]/10 blur-3xl" />
        </section>

        {/* STATS */}
        <section className="grid grid-cols-2 md:grid-cols-3 gap-4 mb-10">
          {[
            {
              label: "Communities",
              value: communities.length,
              icon: Compass,
            },
            {
              label: "Your communities",
              value: communities.filter((c) => c.joined).length,
              icon: Users,
            },
            {
              label: "Interest categories",
              value: categories.length - 1,
              icon: Hash,
            },
          ].map((stat) => {
            const Icon = stat.icon;

            return (
              <div key={stat.label} className="bg-[#F5EBDD] p-5 rounded-2xl">
                <Icon size={23} className="text-[#D87355]" />
                <h3 className="text-2xl font-bold mt-3">{stat.value}</h3>
                <p className="text-sm text-[#927B68] mt-1">{stat.label}</p>
              </div>
            );
          })}
        </section>

        {/* SEARCH */}
        <div className="relative max-w-2xl mb-8">
          <Search
            size={20}
            className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
          />

          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search communities, interests..."
            className="w-full bg-[#F5EBDD] rounded-xl py-4 pl-12 pr-5 outline-none border border-transparent focus:border-[#D87355]"
          />
        </div>

        {/* CATEGORY FILTERS */}
        <div className="flex gap-3 overflow-x-auto pb-4 mb-7">
          {categories.map((category) => (
            <button
              key={category}
              onClick={() => setActiveCategory(category)}
              className={`shrink-0 px-5 py-3 rounded-full text-sm font-semibold transition ${
                activeCategory === category
                  ? "bg-[#0F1B36] text-white"
                  : "bg-[#F5EBDD] text-[#927B68] hover:bg-[#E9D8C5]"
              }`}
            >
              {category}
            </button>
          ))}
        </div>

        {/* COMMUNITY LIST */}
        <section id="community-list">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-2xl font-bold">Explore communities</h2>
              <p className="text-sm text-[#927B68] mt-1">
                {filteredCommunities.length} communities found
              </p>
            </div>
          </div>

          {filteredCommunities.length > 0 ? (
            <div className="grid sm:grid-cols-2 xl:grid-cols-3 gap-6">
              {filteredCommunities.map((community) => (
                <article
                  key={community.id}
                  className="bg-[#F5EBDD] rounded-2xl overflow-hidden shadow-sm hover:-translate-y-1 hover:shadow-xl transition-all"
                >
                  <div
                    className={`h-36 bg-gradient-to-br ${community.cover} flex items-center justify-center relative`}
                  >
                    <span className="text-6xl">{community.emoji}</span>

                    <span className="absolute top-4 right-4 bg-white/90 text-[#0F1B36] text-xs font-semibold px-3 py-2 rounded-full">
                      {community.privacy}
                    </span>
                  </div>

                  <div className="p-5">
                    <span className="text-xs font-bold text-[#D87355]">
                      {community.category}
                    </span>

                    <h3 className="text-xl font-bold mt-2">
                      {community.name}
                    </h3>

                    <p className="text-sm text-[#927B68] leading-relaxed mt-3 min-h-12">
                      {community.description}
                    </p>

                    <div className="flex items-center gap-2 text-sm text-[#927B68] mt-5">
                      <Users size={17} />
                      {community.members.toLocaleString()} members
                    </div>

                    <div className="flex gap-3 mt-6">
                      <button
                        onClick={() => toggleJoin(community.id)}
                        className={`flex-1 py-3 rounded-xl font-semibold flex justify-center items-center gap-2 transition ${
                          community.joined
                            ? "bg-[#E9D8C5] text-[#0F1B36]"
                            : "bg-[#D87355] text-white hover:bg-[#bd6047]"
                        }`}
                      >
                        {community.joined ? (
                          <>
                            <Check size={17} /> Joined
                          </>
                        ) : (
                          <>
                            <Plus size={17} /> Join Community
                          </>
                        )}
                      </button>

                      <Link
                        to={`/communities/${community.id}`}
                        state={{ community }}
                        aria-label={`View ${community.name}`}
                        className="bg-[#0F1B36] text-white p-3 rounded-xl hover:bg-[#24385D] transition"
                      >
                        <ArrowRight size={19} />
                      </Link>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="bg-[#F5EBDD] rounded-2xl p-12 text-center">
              <Search size={35} className="mx-auto text-[#D87355]" />
              <h3 className="text-xl font-bold mt-4">No communities found</h3>
              <p className="text-[#927B68] mt-2">
                Try a different category or search term.
              </p>
              <button
                onClick={() => {
                  setSearch("");
                  setActiveCategory("All");
                }}
                className="mt-5 text-[#D87355] font-bold"
              >
                Clear filters
              </button>
            </div>
          )}
        </section>

        {/* CREATE COMMUNITY MODAL */}
        {showCreateModal && (
          <div className="fixed inset-0 z-50 bg-[#0F1B36]/60 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-[#F5EBDD] rounded-3xl p-7 w-full max-w-lg">
              <div className="flex justify-between items-center mb-7">
                <div>
                  <h2 className="text-2xl font-bold">Create your circle</h2>
                  <p className="text-sm text-[#927B68] mt-1">
                    Bring people together around an interest.
                  </p>
                </div>

                <button
                  onClick={() => setShowCreateModal(false)}
                  aria-label="Close modal"
                  className="text-[#927B68]"
                >
                  <X size={23} />
                </button>
              </div>

              <div className="space-y-5">
                <div>
                  <label className="block font-semibold text-sm mb-2">
                    Community name
                  </label>

                  <input
                    value={newCommunity.name}
                    onChange={(event) =>
                      setNewCommunity({
                        ...newCommunity,
                        name: event.target.value,
                      })
                    }
                    maxLength={50}
                    placeholder="e.g. Creative Minds"
                    className="w-full bg-white border border-[#D3BBA3] rounded-xl p-4 outline-none focus:border-[#D87355]"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-sm mb-2">
                    Category
                  </label>

                  <select
                    value={newCommunity.category}
                    onChange={(event) =>
                      setNewCommunity({
                        ...newCommunity,
                        category: event.target.value,
                      })
                    }
                    className="w-full bg-white border border-[#D3BBA3] rounded-xl p-4 outline-none focus:border-[#D87355]"
                  >
                    {categories.slice(1).map((category) => (
                      <option key={category}>{category}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block font-semibold text-sm mb-2">
                    Description
                  </label>

                  <textarea
                    value={newCommunity.description}
                    onChange={(event) =>
                      setNewCommunity({
                        ...newCommunity,
                        description: event.target.value,
                      })
                    }
                    maxLength={250}
                    rows={4}
                    placeholder="What is your community about?"
                    className="w-full bg-white border border-[#D3BBA3] rounded-xl p-4 outline-none focus:border-[#D87355] resize-none"
                  />
                  <p className="text-xs text-[#927B68] text-right mt-1">
                    {newCommunity.description.length}/250
                  </p>
                </div>
              </div>

              <button
                onClick={createCommunity}
                disabled={
                  !newCommunity.name.trim() ||
                  !newCommunity.description.trim()
                }
                className="w-full bg-[#D87355] text-white py-4 rounded-xl font-semibold mt-7 disabled:opacity-40 hover:bg-[#bd6047] transition"
              >
                Create Community
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
