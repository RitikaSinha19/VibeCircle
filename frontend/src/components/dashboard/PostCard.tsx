
import { useEffect, useState } from "react";
import {
  Heart,
  Share2,
  Bookmark,
} from "lucide-react";

import api from "../../api/axios";
import type { PostView } from "../../api/postApi";
import CommentSection from "./CommentSection";

interface PostCardProps {
  post: PostView;
  onLike: (postId: number, currentlyLiked: boolean) => Promise<void>;
  onCommentCountChange: (postId: number, delta: number) => void;
}

function getTimeAgo(date: string): string {
  const seconds = Math.max(
    0,
    Math.floor((Date.now() - new Date(date).getTime()) / 1000)
  );

  if (seconds < 60) return "Just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  if (seconds < 604800) return `${Math.floor(seconds / 86400)}d ago`;

  return new Date(date).toLocaleDateString();
}

function PostMedia({
  url,
  type,
}: {
  url: string;
  type: string;
}) {
  const [mediaUrl, setMediaUrl] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    async function loadMedia() {
      try {
        const response = await api.get(url, {
          responseType: "blob",
        });

        objectUrl = URL.createObjectURL(response.data);

        if (!cancelled) {
          setMediaUrl(objectUrl);
        } else {
          URL.revokeObjectURL(objectUrl);
        }
      } catch (error) {
        console.error("Unable to load post media:", error);
      }
    }

    loadMedia();

    return () => {
      cancelled = true;

      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [url]);

  if (!mediaUrl) {
    return (
      <div className="mt-4 h-48 rounded-xl bg-[#E9D8C5] flex items-center justify-center text-sm text-[#927B68]">
        Loading media...
      </div>
    );
  }

  if (type.toUpperCase() === "VIDEO") {
    return (
      <video
        src={mediaUrl}
        controls
        className="mt-4 w-full max-h-[500px] rounded-xl object-contain bg-black"
      />
    );
  }

  return (
    <img
      src={mediaUrl}
      alt="Post attachment"
      className="mt-4 w-full max-h-[500px] rounded-xl object-contain"
    />
  );
}

export default function PostCard({
  post,
  onLike,
  onCommentCountChange,
}: PostCardProps) {
  const [saved, setSaved] = useState(false);
  const [likeLoading, setLikeLoading] = useState(false);
  const [shareMessage, setShareMessage] = useState("");

  const initials = post.author.fullName
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();

  async function handleLike() {
    if (likeLoading) return;

    setLikeLoading(true);

    try {
      await onLike(post.id, post.likedByMe);
    } finally {
      setLikeLoading(false);
    }
  }

  async function handleShare() {
    const postUrl = `${window.location.origin}/posts/${post.id}`;

    try {
      if (navigator.share) {
        await navigator.share({
          title: `Post by ${post.author.fullName}`,
          text: post.caption ?? "Check out this post on VibeCircle!",
          url: postUrl,
        });
      } else {
        await navigator.clipboard.writeText(postUrl);
        setShareMessage("Link copied!");

        window.setTimeout(() => {
          setShareMessage("");
        }, 2000);
      }
    } catch {
      // Sharing may be cancelled by the user.
    }
  }

  return (
    <article className="bg-[#F5EBDD] rounded-2xl p-5 shadow-sm mt-5">

      {/* POST AUTHOR */}
      <div className="flex items-center gap-3">
        <div className="w-11 h-11 shrink-0 rounded-full bg-[#0F1B36] text-white flex items-center justify-center font-bold">
          {initials || "U"}
        </div>

        <div className="flex-1 min-w-0">
          <h3 className="font-bold truncate">
            {post.author.fullName}
          </h3>

          <p className="text-xs text-[#927B68]">
            @{post.author.username} · {getTimeAgo(post.createdAt)}
          </p>
        </div>

        <span className="text-xs bg-[#E9D8C5] px-3 py-2 rounded-full">
          {post.cityName || post.audience.replace("_", " ")}
        </span>
      </div>

      {/* POST CAPTION */}
      {post.caption && (
        <p className="mt-5 leading-relaxed text-[#0F1B36] whitespace-pre-wrap break-words">
          {post.caption}
        </p>
      )}

      {/* HASHTAGS */}
      {post.hashtags.length > 0 && (
        <div className="flex flex-wrap gap-2 mt-3">
          {post.hashtags.map((tag) => (
            <span
              key={tag}
              className="text-sm text-[#D87355]"
            >
              #{tag}
            </span>
          ))}
        </div>
      )}

      {/* POST MEDIA */}
      {post.media.map((media) => (
        <PostMedia
          key={media.id}
          url={media.url}
          type={media.type}
        />
      ))}

      {/* POST ACTIONS */}
      <div className="flex items-center justify-between border-t border-[#D3BBA3] mt-5 pt-4">

        <div className="flex items-center gap-6">

          {/* LIKE */}
          <button
            type="button"
            onClick={handleLike}
            disabled={likeLoading}
            className={`flex items-center gap-2 disabled:opacity-50 ${
              post.likedByMe
                ? "text-[#D87355]"
                : "text-[#927B68]"
            }`}
          >
            <Heart
              size={20}
              fill={post.likedByMe ? "currentColor" : "none"}
            />

            {post.likeCount}
          </button>

          {/* SHARE */}
          <button
            type="button"
            onClick={handleShare}
            className="text-[#927B68]"
            title="Share post"
          >
            <Share2 size={19} />
          </button>

          {shareMessage && (
            <span className="text-xs text-[#D87355]">
              {shareMessage}
            </span>
          )}
        </div>

        {/* BOOKMARK */}
        <button
          type="button"
          onClick={() => setSaved((previous) => !previous)}
          className={saved ? "text-[#D87355]" : "text-[#927B68]"}
          title="Save post locally"
        >
          <Bookmark
            size={20}
            fill={saved ? "currentColor" : "none"}
          />
        </button>
      </div>

      {/* COMMENTS SECTION */}
      <CommentSection
        postId={post.id}
        commentCount={post.commentCount}
        onCountChange={(delta) =>
          onCommentCountChange(post.id, delta)
        }
      />

    </article>
  );
}
