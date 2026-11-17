
import { useMemo, useState } from "react";
import {
  Search,
  Heart,
  Bookmark,
  Compass,
  Users,
  ArrowUpRight,
} from "lucide-react";
import Sidebar from "../components/dashboard/Sidebar";

const categories = [
  "All",
  "Art & Design",
  "Technology",
  "Photography",
  "Music",
  "Travel",
  "Books",
  "Lifestyle",
];

const discoveries = [
  {
    id: 1,
    title: "The beauty of creating something from nothing.",
    author: "Ananya Sharma",
    username: "ananya.creates",
    category: "Art & Design",
    image: "photo-1513364776144-60967b0f800f",
    height: "h-72",
    likes: 128,
    color: "bg-[#8C6B85]",
    initials: "A",
  },
  {
    id: 2,
    title: "Building ideas, one line of code at a time.",
    author: "Rahul Verma",
    username: "rahul.codes",
    category: "Technology",
    image: "photo-1518770660439-4636190af475",
    height: "h-52",
    likes: 86,
    color: "bg-[#507C73]",
    initials: "R",
  },
  {
    id: 3,
    title: "Somewhere between mountains and dreams.",
    author: "Priya Das",
    username: "priya.travels",
    category: "Travel",
    image: "photo-1464822759023-fed622ff2c3b",
    height: "h-80",
    likes: 204,
    color: "bg-[#C18459]",
    initials: "P",
  },
  {
    id: 4,
    title: "Little moments, beautifully captured.",
    author: "Sneha Roy",
    username: "sneha.frames",
    category: "Photography",
    image: "photo-1470252649378-9c29740c9fa8",
    height: "h-64",
    likes: 156,
    color: "bg-[#A66C61]",
    initials: "S",
  },
  {
    id: 5,
    title: "A world of stories waiting to be discovered.",
    author: "Ishita Sen",
    username: "ishita.reads",
    category: "Books",
    image: "photo-1512820790803-83ca734da794",
    height: "h-72",
    likes: 93,
    color: "bg-[#8C6B85]",
    initials: "I",
  },
  {
    id: 6,
    title: "Music makes ordinary days special.",
    author: "Arjun Mehta",
    username: "arjun.music",
    category: "Music",
    image: "photo-1516280440614-37939bbacd81",
    height: "h-56",
    likes: 117,
    color: "bg-[#637A9B]",
    initials: "A",
  },
  {
    id: 7,
    title: "Finding beauty in the everyday.",
    author: "Meera Kapoor",
    username: "meera.lifestyle",
    category: "Lifestyle",
    image: "photo-1490750967868-88aa4486c946",
    height: "h-64",
    likes: 75,
    color: "bg-[#C18459]",
    initials: "M",
  },
  {
    id: 8,
    title: "Ideas become real when we start building.",
    author: "Karan Shah",
    username: "karan.builds",
    category: "Technology",
    image: "photo-1451187580459-43490279c0fa",
    height: "h-72",
    likes: 142,
    color: "bg-[#507C73]",
    initials: "K",
  },
];

const people = [
  { name: "Ananya Sharma", handle: "@ananya.creates", initials: "A", color: "bg-[#8C6B85]", interest: "Art & Design" },
  { name: "Rahul Verma", handle: "@rahul.codes", initials: "R", color: "bg-[#507C73]", interest: "Technology" },
  { name: "Priya Das", handle: "@priya.travels", initials: "P", color: "bg-[#C18459]", interest: "Travel" },
];

export default function ExplorePage() {
  const [activeCategory, setActiveCategory] = useState("All");
  const [search, setSearch] = useState("");
  const [savedPosts, setSavedPosts] = useState<number[]>([]);
  const [followedPeople, setFollowedPeople] = useState<string[]>([]);

  const filteredDiscoveries = useMemo(() => {
    return discoveries.filter((post) => {
      const matchesCategory =
        activeCategory === "All" || post.category === activeCategory;

      const query = search.toLowerCase();

      const matchesSearch =
        post.title.toLowerCase().includes(query) ||
        post.author.toLowerCase().includes(query) ||
        post.category.toLowerCase().includes(query);

      return matchesCategory && matchesSearch;
    });
  }, [activeCategory, search]);

  function toggleSave(id: number) {
    setSavedPosts((previous) =>
      previous.includes(id)
        ? previous.filter((postId) => postId !== id)
        : [...previous, id]
    );
  }

  function toggleFollow(name: string) {
    setFollowedPeople((previous) =>
      previous.includes(name)
        ? previous.filter((person) => person !== name)
        : [...previous, name]
    );
  }

  return (
    <div className="min-h-screen bg-[#D3BBA3] flex text-[#0F1B36]">
      <Sidebar />

      <main className="flex-1 min-w-0 px-5 md:px-10 py-6">

        {/* HEADER */}
        <header className="flex items-center justify-between gap-4 mb-10">
          <div>
            <p className="text-[#D87355] font-bold text-xs tracking-widest uppercase">
              Discover something new
            </p>
            <h1 className="text-3xl md:text-4xl font-bold mt-2">
              Explore your world.
            </h1>
            <p className="text-[#927B68] mt-2">
              Find inspiration, people and communities.
            </p>
          </div>

          <div className="hidden sm:flex items-center gap-2 bg-[#0F1B36] text-white rounded-full px-5 py-3">
            <Compass size={19} className="text-[#E9A58D]" />
            <span className="font-semibold text-sm">Discover</span>
          </div>
        </header>

        {/* SEARCH */}
        <div className="relative max-w-2xl mb-8">
          <Search
            className="absolute left-5 top-1/2 -translate-y-1/2 text-[#927B68]"
            size={21}
          />

          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search interests, people, ideas..."
            className="w-full bg-[#F5EBDD] rounded-2xl py-5 pl-14 pr-5 outline-none border border-transparent focus:border-[#D87355] shadow-sm"
          />
        </div>

        {/* CATEGORIES */}
        <section className="mb-10">
          <div className="flex gap-3 overflow-x-auto pb-3">
            {categories.map((category) => (
              <button
                key={category}
                onClick={() => setActiveCategory(category)}
                className={`shrink-0 px-5 py-3 rounded-full font-semibold text-sm transition ${
                  activeCategory === category
                    ? "bg-[#0F1B36] text-white"
                    : "bg-[#F5EBDD] text-[#927B68] hover:bg-[#E9D8C5]"
                }`}
              >
                {category}
              </button>
            ))}
          </div>
        </section>

        {/* PEOPLE TO DISCOVER */}
        <section className="mb-12">
          <div className="flex justify-between items-center mb-5">
            <div>
              <h2 className="text-2xl font-bold">People you might like</h2>
              <p className="text-[#927B68] text-sm mt-1">
                Meet people who share your interests.
              </p>
            </div>
            <Users className="text-[#D87355]" size={23} />
          </div>

          <div className="grid sm:grid-cols-2 xl:grid-cols-3 gap-4">
            {people.map((person) => {
              const isFollowing = followedPeople.includes(person.name);

              return (
                <div
                  key={person.name}
                  className="bg-[#F5EBDD] rounded-2xl p-5 flex items-center gap-4 shadow-sm"
                >
                  <div className={`w-12 h-12 shrink-0 rounded-full ${person.color} text-white flex items-center justify-center font-bold text-lg`}>
                    {person.initials}
                  </div>

                  <div className="min-w-0 flex-1">
                    <p className="font-bold truncate">{person.name}</p>
                    <p className="text-xs text-[#927B68] truncate">
                      {person.handle}
                    </p>
                    <p className="text-xs text-[#D87355] mt-1">
                      {person.interest}
                    </p>
                  </div>

                  <button
                    onClick={() => toggleFollow(person.name)}
                    className={`shrink-0 text-xs font-bold px-3 py-2 rounded-lg transition ${
                      isFollowing
                        ? "bg-[#E9D8C5] text-[#0F1B36]"
                        : "bg-[#D87355] text-white"
                    }`}
                  >
                    {isFollowing ? "Following" : "Follow"}
                  </button>
                </div>
              );
            })}
          </div>
        </section>

        {/* DISCOVERY FEED */}
        <section>
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-2xl font-bold">Made for your curiosity.</h2>
              <p className="text-sm text-[#927B68] mt-1">
                Ideas and moments from across the community.
              </p>
            </div>
            <ArrowUpRight className="text-[#D87355]" />
          </div>

          {filteredDiscoveries.length > 0 ? (
            <div className="columns-1 sm:columns-2 xl:columns-3 gap-5">
              {filteredDiscoveries.map((post) => {
                const isSaved = savedPosts.includes(post.id);

                return (
                  <article
                    key={post.id}
                    className="break-inside-avoid mb-5 bg-[#F5EBDD] rounded-2xl overflow-hidden shadow-sm hover:-translate-y-1 hover:shadow-xl transition-all"
                  >
                    <div className="relative">
                      <img
                        src={`https://images.unsplash.com/${post.image}?auto=format&fit=crop&w=700&q=80`}
                        alt={post.title}
                        loading="lazy"
                        className={`w-full ${post.height} object-cover`}
                      />

                      <button
                        onClick={() => toggleSave(post.id)}
                        aria-label={isSaved ? "Remove saved post" : "Save post"}
                        className={`absolute top-3 right-3 p-3 rounded-full shadow-md transition ${
                          isSaved
                            ? "bg-[#D87355] text-white"
                            : "bg-[#F5EBDD] text-[#0F1B36]"
                        }`}
                      >
                        <Bookmark
                          size={19}
                          fill={isSaved ? "currentColor" : "none"}
                        />
                      </button>

                      <span className="absolute bottom-3 left-3 bg-[#0F1B36]/80 text-white text-xs px-3 py-2 rounded-full">
                        {post.category}
                      </span>
                    </div>

                    <div className="p-4">
                      <h3 className="font-bold leading-relaxed">
                        {post.title}
                      </h3>

                      <div className="flex items-center justify-between mt-5">
                        <div className="flex items-center gap-2 min-w-0">
                          <div className={`w-8 h-8 shrink-0 rounded-full ${post.color} text-white flex items-center justify-center text-xs font-bold`}>
                            {post.initials}
                          </div>

                          <span className="text-xs font-semibold truncate">
                            {post.author}
                          </span>
                        </div>

                        <div className="flex items-center gap-1 text-[#927B68] text-xs">
                          <Heart size={15} />
                          {post.likes}
                        </div>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="bg-[#F5EBDD] rounded-2xl p-12 text-center">
              <Search size={35} className="mx-auto text-[#D87355]" />
              <h3 className="font-bold text-xl mt-4">Nothing found yet</h3>
              <p className="text-[#927B68] mt-2">
                Try another keyword or explore a different interest.
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

        <footer className="text-center text-[#927B68] text-sm py-12">
          Keep exploring. Your next connection might be one discovery away. 🤎
        </footer>
      </main>
    </div>
  );
}
