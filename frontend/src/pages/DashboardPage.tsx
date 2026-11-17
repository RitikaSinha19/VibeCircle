
import { useCallback, useEffect, useState } from "react";
import { AxiosError } from "axios";

import Sidebar from "../components/dashboard/Sidebar";
import Topbar from "../components/dashboard/Topbar";
import Stories from "../components/dashboard/Stories";
import CreatePost from "../components/dashboard/CreatePost";
import PostCard from "../components/dashboard/PostCard";
import RightSidebar from "../components/dashboard/RightSidebar";

import {
  postApi,
  type FeedScope,
  type PostView,
} from "../api/postApi";

export default function DashboardPage() {
  const [posts, setPosts] = useState<PostView[]>([]);
  const [scope, setScope] = useState<FeedScope>("ALL");

  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [isPosting, setIsPosting] = useState(false);

  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [hasNext, setHasNext] = useState(false);

  const [error, setError] = useState("");

  const getErrorMessage = (err: unknown): string => {
    if (err instanceof AxiosError) {
      return (
        (err.response?.data as { message?: string } | undefined)?.message ||
        err.message ||
        "Something went wrong. Please try again."
      );
    }

    return err instanceof Error
      ? err.message
      : "Something went wrong. Please try again.";
  };

  const loadFeed = useCallback(async (selectedScope: FeedScope) => {
    setLoading(true);
    setError("");
    setPosts([]);
    setNextCursor(null);
    setHasNext(false);

    try {
      const page = await postApi.getFeed(selectedScope);

      setPosts(page.items);
      setNextCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFeed(scope);
  }, [scope, loadFeed]);

  async function handleNewPost(content: string, files: File[]) {
    setIsPosting(true);
    setError("");

    try {
      const createdPost = await postApi.createPost(content, files);

      setPosts((previous) => [
        createdPost,
        ...previous.filter((post) => post.id !== createdPost.id),
      ]);
    } catch (err) {
      setError(getErrorMessage(err));
      throw err;
    } finally {
      setIsPosting(false);
    }
  }

  async function handleLike(
    postId: number,
    currentlyLiked: boolean
  ) {
    try {
      const result = currentlyLiked
        ? await postApi.unlikePost(postId)
        : await postApi.likePost(postId);

      setPosts((previous) =>
        previous.map((post) =>
          post.id === postId
            ? {
                ...post,
                likedByMe: result.liked,
                likeCount: result.likeCount,
              }
            : post
        )
      );
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function handleLoadMore() {
    if (!nextCursor || loadingMore) return;

    setLoadingMore(true);
    setError("");

    try {
      const page = await postApi.getFeed(scope, nextCursor);

      setPosts((previous) => {
        const existingIds = new Set(previous.map((post) => post.id));

        return [
          ...previous,
          ...page.items.filter((post) => !existingIds.has(post.id)),
        ];
      });

      setNextCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoadingMore(false);
    }
  }

  // Update the comment count when a comment is added or deleted.
  function handleCommentCountChange(postId: number, delta: number) {
    setPosts((previous) =>
      previous.map((post) =>
        post.id === postId
          ? {
              ...post,
              commentCount: Math.max(0, post.commentCount + delta),
            }
          : post
      )
    );
  }

  return (
    <div className="min-h-screen bg-[#D3BBA3] text-[#0F1B36] flex">
      <Sidebar />

      <main className="flex-1 min-w-0 px-5 md:px-8 xl:px-10">
        <Topbar />

        <div className="max-w-7xl mx-auto flex gap-8 py-6">
          <section className="flex-1 min-w-0 max-w-3xl">
            <div className="mb-8">
              <p className="text-[#D87355] font-bold text-sm tracking-widest uppercase">
                Your social space
              </p>

              <h1 className="text-3xl md:text-4xl font-bold mt-2">
                Good to see you again! 🤎
              </h1>

              <p className="text-[#927B68] mt-2">
                Here's what's happening in your circle today.
              </p>
            </div>

            <Stories />

            <CreatePost
              onPost={handleNewPost}
              isPosting={isPosting}
            />

            <div className="flex flex-wrap justify-between items-center gap-3 mt-9 mb-4">
              <h2 className="text-xl font-bold">Your Feed</h2>

              <select
                value={scope}
                onChange={(event) =>
                  setScope(event.target.value as FeedScope)
                }
                className="bg-[#F5EBDD] text-[#D87355] text-sm font-semibold px-3 py-2 rounded-xl outline-none cursor-pointer"
              >
                <option value="ALL">For You</option>
                <option value="FRIENDS">Friends</option>
              </select>
            </div>

            {error && (
              <div className="bg-red-100 text-red-700 p-4 rounded-xl mt-4">
                <p>{error}</p>
                <button
                  type="button"
                  onClick={() => void loadFeed(scope)}
                  className="font-semibold underline mt-2"
                >
                  Retry loading feed
                </button>
              </div>
            )}

            {loading ? (
              <div className="bg-[#F5EBDD] rounded-2xl p-8 mt-5 text-center text-[#927B68]">
                Loading your feed...
              </div>
            ) : posts.length === 0 ? (
              <div className="bg-[#F5EBDD] rounded-2xl p-8 mt-5 text-center">
                <h3 className="font-bold text-lg">Your feed is quiet.</h3>
                <p className="text-[#927B68] mt-2">
                  Be the first to share something with your circle.
                </p>
              </div>
            ) : (
              <>
                {posts.map((post) => (
                  <PostCard
                    key={post.id}
                    post={post}
                    onLike={handleLike}
                    onCommentCountChange={handleCommentCountChange}
                  />
                ))}

                {hasNext && (
                  <button
                    type="button"
                    onClick={handleLoadMore}
                    disabled={loadingMore}
                    className="w-full mt-6 py-3 rounded-xl bg-[#0F1B36] text-white font-semibold disabled:opacity-50"
                  >
                    {loadingMore ? "Loading..." : "Load more posts"}
                  </button>
                )}
              </>
            )}
          </section>

          <RightSidebar />
        </div>
      </main>
    </div>
  );
}
