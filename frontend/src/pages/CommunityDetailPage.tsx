
import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Users,
  MessageCircle,
  Heart,
  Send,
  
} from "lucide-react";
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

const samplePosts = [
  {
    id: 1,
    author: "Ananya Sharma",
    initials: "A",
    color: "bg-[#8C6B85]",
    time: "30 minutes ago",
    content:
      "What inspired everyone to join this community? I'd love to hear your stories! 🤎",
    likes: 24,
    comments: 8,
  },
  {
    id: 2,
    author: "Rahul Verma",
    initials: "R",
    color: "bg-[#507C73]",
    time: "2 hours ago",
    content:
      "Sharing a little project I've been working on recently. Excited to learn and grow with everyone here!",
    likes: 18,
    comments: 5,
  },
];

export default function CommunityDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  const community = location.state?.community as Community | undefined;

  const [joined, setJoined] = useState(community?.joined ?? false);
  const [postContent, setPostContent] = useState("");
  const [posts, setPosts] = useState(samplePosts);

  if (!community || community.id !== Number(id)) {
    return (
      <div className="min-h-screen bg-[#D3BBA3] flex">
        <Sidebar />
        <main className="flex-1 p-10">
          <button
            onClick={() => navigate("/communities")}
            className="text-[#D87355] font-semibold"
          >
            <ArrowLeft className="inline mr-2" size={18} />
            Back to communities
          </button>

          <div className="mt-16 text-center">
            <h1 className="text-3xl font-bold">Community not available</h1>
            <p className="text-[#927B68] mt-3">
              Please return to Explore Communities and open a community again.
            </p>
          </div>
        </main>
      </div>
    );
  }

  function publishPost() {
    if (!postContent.trim() || !joined) return;

    setPosts((previous) => [
      {
        id: Date.now(),
        author: "You",
        initials: "Y",
        color: "bg-[#D87355]",
        time: "Just now",
        content: postContent.trim(),
        likes: 0,
        comments: 0,
      },
      ...previous,
    ]);

    setPostContent("");
  }

  return (
    <div className="min-h-screen bg-[#D3BBA3] flex text-[#0F1B36]">
      <Sidebar />

      <main className="flex-1 min-w-0 px-5 md:px-10 py-7">

        <button
          onClick={() => navigate("/communities")}
          className="flex items-center gap-2 text-[#927B68] hover:text-[#D87355] font-semibold mb-7"
        >
          <ArrowLeft size={19} />
          Back to communities
        </button>

        {/* COMMUNITY COVER */}
        <section
          className={`bg-gradient-to-br ${community.cover} rounded-3xl p-8 md:p-12 text-white relative overflow-hidden`}
        >
          <div className="relative z-10 max-w-2xl">
            <span className="text-sm text-white/70 font-semibold tracking-widest">
              {community.category.toUpperCase()}
            </span>

            <h1 className="text-4xl md:text-5xl font-bold mt-4">
              {community.emoji} {community.name}
            </h1>

            <p className="text-white/80 mt-5 leading-relaxed">
              {community.description}
            </p>

            <div className="flex flex-wrap items-center gap-5 mt-7 text-sm">
              <span className="flex items-center gap-2">
                <Users size={18} />
                {community.members.toLocaleString()} members
              </span>

              <span>{community.privacy} Community</span>
            </div>

            <button
              onClick={() => setJoined(!joined)}
              className={`mt-8 px-7 py-3 rounded-xl font-semibold transition ${
                joined
                  ? "bg-white/20 text-white border border-white/30"
                  : "bg-[#D87355] text-white hover:bg-[#bd6047]"
              }`}
            >
              {joined ? "✓ Joined Community" : "Join Community"}
            </button>
          </div>

          <div className="absolute -right-20 -bottom-32 w-96 h-96 border-[50px] border-white/10 rounded-full" />
        </section>

        {/* COMMUNITY CONTENT */}
        <div className="grid xl:grid-cols-[1fr_300px] gap-8 mt-9">

          <section>
            <div className="mb-6">
              <h2 className="text-2xl font-bold">Community discussions</h2>
              <p className="text-sm text-[#927B68] mt-1">
                Ideas, conversations and moments shared by members.
              </p>
            </div>

            {/* CREATE POST */}
            <div className="bg-[#F5EBDD] p-5 rounded-2xl shadow-sm mb-7">
              <textarea
                value={postContent}
                onChange={(event) => setPostContent(event.target.value)}
                disabled={!joined}
                placeholder={
                  joined
                    ? "Share something with your community..."
                    : "Join this community to start participating."
                }
                rows={3}
                className="w-full bg-transparent outline-none resize-none placeholder:text-[#927B68] disabled:opacity-50"
              />

              <div className="flex justify-end border-t border-[#D3BBA3] pt-4 mt-3">
                <button
                  onClick={publishPost}
                  disabled={!joined || !postContent.trim()}
                  className="flex items-center gap-2 bg-[#D87355] text-white px-5 py-3 rounded-xl font-semibold disabled:opacity-40"
                >
                  <Send size={16} />
                  Publish
                </button>
              </div>
            </div>

            {/* POSTS */}
            <div className="space-y-5">
              {posts.map((post) => (
                <article
                  key={post.id}
                  className="bg-[#F5EBDD] rounded-2xl p-6"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-11 h-11 rounded-full ${post.color} text-white flex items-center justify-center font-bold`}
                    >
                      {post.initials}
                    </div>

                    <div>
                      <h3 className="font-bold">{post.author}</h3>
                      <p className="text-xs text-[#927B68]">{post.time}</p>
                    </div>
                  </div>

                  <p className="mt-5 leading-relaxed">{post.content}</p>

                  <div className="flex gap-7 border-t border-[#D3BBA3] mt-5 pt-4 text-[#927B68] text-sm">
                    <span className="flex items-center gap-2">
                      <Heart size={18} /> {post.likes}
                    </span>

                    <span className="flex items-center gap-2">
                      <MessageCircle size={18} /> {post.comments}
                    </span>
                  </div>
                </article>
              ))}
            </div>
          </section>

          {/* ABOUT SIDEBAR */}
          <aside className="space-y-5">
            <div className="bg-[#F5EBDD] rounded-2xl p-6">
              <h3 className="text-xl font-bold">About this community</h3>

              <p className="text-sm text-[#927B68] leading-relaxed mt-4">
                {community.description}
              </p>

              <div className="border-t border-[#D3BBA3] mt-5 pt-5 space-y-4 text-sm">
                <p className="flex justify-between">
                  <span className="text-[#927B68]">Category</span>
                  <strong>{community.category}</strong>
                </p>

                <p className="flex justify-between">
                  <span className="text-[#927B68]">Visibility</span>
                  <strong>{community.privacy}</strong>
                </p>

                <p className="flex justify-between">
                  <span className="text-[#927B68]">Members</span>
                  <strong>{community.members.toLocaleString()}</strong>
                </p>
              </div>
            </div>

            <div className="bg-[#0F1B36] text-white rounded-2xl p-6">
              <Users size={26} className="text-[#E9A58D]" />
              <h3 className="text-xl font-bold mt-4">Better together.</h3>
              <p className="text-white/60 text-sm mt-2 leading-relaxed">
                Connect with people who share your interests and make every
                conversation count.
              </p>
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}
