
import { useState } from "react";
import { MessageCircle, Send, Trash2, ChevronDown } from "lucide-react";
import { AxiosError } from "axios";
import { commentApi, type CommentView } from "../../api/commentApi";

interface CommentSectionProps {
  postId: number;
  commentCount: number;
  onCountChange: (delta: number) => void;
}

function timeAgo(date: string): string {
  const seconds = Math.max(
    0,
    Math.floor((Date.now() - new Date(date).getTime()) / 1000)
  );

  if (seconds < 60) return "Just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;

  return new Date(date).toLocaleDateString();
}

function errorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    return (
      (error.response?.data as { message?: string } | undefined)?.message ||
      "Something went wrong."
    );
  }

  return "Something went wrong. Please try again.";
}

export default function CommentSection({
  postId,
  commentCount,
  onCountChange,
}: CommentSectionProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [comments, setComments] = useState<CommentView[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);

  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [body, setBody] = useState("");
  const [error, setError] = useState("");

  async function loadComments(reset = false) {
    if (loading) return;

    setLoading(true);
    setError("");

    try {
      const requestedPage = reset ? 0 : page;
      const result = await commentApi.getComments(postId, requestedPage);

      setComments((previous) =>
        reset ? result.items : [...previous, ...result.items]
      );

      setPage(result.page + 1);
      setHasNext(result.hasNext);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  async function toggleComments() {
    if (!isOpen && comments.length === 0) {
      await loadComments(true);
    }

    setIsOpen((previous) => !previous);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const text = body.trim();
    if (!text || submitting) return;

    setSubmitting(true);
    setError("");

    try {
      const created = await commentApi.createComment(postId, text);

      setComments((previous) => [created, ...previous]);
      setBody("");
      onCountChange(1);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(commentId: number) {
    setError("");

    try {
      await commentApi.deleteComment(postId, commentId);

      setComments((previous) =>
        previous.filter((comment) => comment.id !== commentId)
      );

      onCountChange(-1);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <div className="mt-4 border-t border-[#D3BBA3] pt-4">
      <button
        type="button"
        onClick={toggleComments}
        className="flex items-center gap-2 text-[#927B68] font-medium"
      >
        <MessageCircle size={19} />
        {commentCount} Comments
        <ChevronDown
          size={16}
          className={`transition-transform ${isOpen ? "rotate-180" : ""}`}
        />
      </button>

      {isOpen && (
        <div className="mt-4">
          <form onSubmit={handleSubmit} className="flex gap-2">
            <input
              value={body}
              onChange={(event) => setBody(event.target.value)}
              placeholder="Write a comment..."
              maxLength={1000}
              className="flex-1 min-w-0 bg-white/70 border border-[#D3BBA3] rounded-xl px-4 py-2 outline-none focus:border-[#D87355]"
            />

            <button
              type="submit"
              disabled={!body.trim() || submitting}
              className="bg-[#D87355] text-white p-3 rounded-xl disabled:opacity-40"
              aria-label="Send comment"
            >
              <Send size={17} />
            </button>
          </form>

          {error && (
            <p className="text-sm text-red-600 mt-3">{error}</p>
          )}

          <div className="mt-4 space-y-4">
            {loading && comments.length === 0 && (
              <p className="text-sm text-[#927B68]">Loading comments...</p>
            )}

            {!loading && comments.length === 0 && !error && (
              <p className="text-sm text-[#927B68]">
                No comments yet. Start the conversation!
              </p>
            )}

            {comments.map((comment) => (
              <div key={comment.id} className="flex gap-3">
                <div className="w-9 h-9 shrink-0 rounded-full bg-[#0F1B36] text-white flex items-center justify-center text-sm font-bold">
                  {comment.author.fullName.charAt(0).toUpperCase()}
                </div>

                <div className="flex-1 min-w-0 bg-white/60 rounded-xl p-3">
                  <div className="flex justify-between items-start gap-2">
                    <div className="min-w-0">
                      <p className="font-semibold text-sm">
                        {comment.author.fullName}
                      </p>

                      <p className="text-xs text-[#927B68]">
                        @{comment.author.username} · {timeAgo(comment.createdAt)}
                      </p>
                    </div>

                    {comment.mine && (
                      <button
                        type="button"
                        onClick={() => handleDelete(comment.id)}
                        className="text-[#927B68] hover:text-red-600"
                        title="Delete comment"
                      >
                        <Trash2 size={16} />
                      </button>
                    )}
                  </div>

                  <p className="text-sm mt-2 whitespace-pre-wrap break-words">
                    {comment.body}
                  </p>
                </div>
              </div>
            ))}
          </div>

          {hasNext && (
            <button
              type="button"
              onClick={() => loadComments()}
              disabled={loading}
              className="text-sm text-[#D87355] font-semibold mt-4 disabled:opacity-50"
            >
              {loading ? "Loading..." : "Load older comments"}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
