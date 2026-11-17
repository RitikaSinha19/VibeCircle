import { useEffect, useRef, useState } from "react";
import { Plus, X, ChevronLeft, ChevronRight } from "lucide-react";
import { storyApi, type StoryTrayItem, type StoryView } from "../../api/storyApi";
import useAuthStore from "../../store/authStore";

export default function Stories() {
  const user = useAuthStore((state) => state.user);
  const [tray, setTray] = useState<StoryTrayItem[]>([]);
  const [myStories, setMyStories] = useState<StoryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [composerOpen, setComposerOpen] = useState(false);
  const [storyText, setStoryText] = useState("");
  const [storyFile, setStoryFile] = useState<File | null>(null);
  const [audience, setAudience] = useState<"PUBLIC" | "FRIENDS_ONLY">("FRIENDS_ONLY");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [activeStories, setActiveStories] = useState<StoryView[]>([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [mediaObjectUrl, setMediaObjectUrl] = useState("");
  const [viewerLoading, setViewerLoading] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const refreshStories = async () => {
    const [nextTray, ownStories] = await Promise.all([
      storyApi.getTray(),
      storyApi.getMine(),
    ]);
    setTray(nextTray);
    setMyStories(ownStories);
  };

  useEffect(() => {
    let alive = true;
    Promise.all([storyApi.getTray(), storyApi.getMine()])
      .then(([nextTray, ownStories]) => {
        if (!alive) return;
        setTray(nextTray);
        setMyStories(ownStories);
      })
      .catch((err: unknown) => {
        console.error("Could not load stories:", err);
        if (alive) setError("Stories could not be loaded. Please try again.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    let alive = true;
    let objectUrl = "";
    const current = activeStories[activeIndex];
    setMediaObjectUrl("");
    if (!current?.mediaUrl) return;
    setViewerLoading(true);
    storyApi.getMedia(current.mediaUrl)
      .then((url) => {
        objectUrl = url;
        if (alive) setMediaObjectUrl(url);
        else URL.revokeObjectURL(url);
      })
      .catch((err: unknown) => {
        console.error("Could not load story media:", err);
        if (alive) setError("This story's media could not be loaded.");
      })
      .finally(() => { if (alive) setViewerLoading(false); });
    return () => {
      alive = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [activeStories, activeIndex]);

  const openStories = async (userId: number) => {
    setError("");
    try {
      const items = user?.id === userId
        ? myStories
        : await storyApi.getByUser(userId);
      if (items.length) {
        setActiveStories(items);
        setActiveIndex(0);
        void storyApi.markViewed(items[0].id).catch((err) => console.error("Story view update failed:", err));
      }
    } catch (err) {
      console.error("Could not open stories:", err);
      setError("Could not open these stories.");
    }
  };

  const submitStory = async () => {
    if ((!storyText.trim() && !storyFile) || saving) return;
    setSaving(true);
    setError("");
    try {
      await storyApi.create(storyText, storyFile, audience);
      setStoryText("");
      setStoryFile(null);
      if (fileInput.current) fileInput.current.value = "";
      setComposerOpen(false);
      await refreshStories();
    } catch (err) {
      console.error("Story creation failed:", err);
      setError("Your story could not be posted. Check the file type/size and try again.");
    } finally {
      setSaving(false);
    }
  };

  const closeViewer = () => {
    setActiveStories([]);
    setActiveIndex(0);
  };

  const moveStory = (direction: number) => {
    const next = activeIndex + direction;
    if (next < 0 || next >= activeStories.length) {
      closeViewer();
      return;
    }
    setActiveIndex(next);
    void storyApi.markViewed(activeStories[next].id).catch((err) => console.error("Story view update failed:", err));
  };

  return (
    <section className="mt-8">
      <div className="flex items-center justify-between mb-5">
        <h2 className="text-xl font-bold">Your Circle's Moments</h2>
        <button type="button" onClick={() => setComposerOpen(true)} className="text-sm font-semibold text-[#D87355] hover:underline">
          Create story
        </button>
      </div>

      {error && <p role="alert" className="mb-3 rounded-xl bg-red-100 px-4 py-2 text-sm text-red-700">{error}</p>}

      <div className="flex gap-6 overflow-x-auto pb-4">
        <button type="button" onClick={() => myStories.length ? void openStories(user?.id ?? 0) : setComposerOpen(true)} className="flex flex-col items-center gap-2 min-w-[65px]">
          <span className="relative flex w-16 h-16 items-center justify-center rounded-full bg-[#D87355] text-white ring-4 ring-[#F5EBDD] outline outline-2 outline-[#D87355]">
            {myStories.length ? <span className="text-xl font-bold">{(user?.fullName || "Y").charAt(0).toUpperCase()}</span> : <Plus size={27} />}
            {myStories.length > 0 && <span className="absolute -bottom-1 -right-1 rounded-full bg-[#0F1B36] p-1"><Plus size={12} /></span>}
          </span>
          <span className="text-xs font-medium text-[#0F1B36]">Your Story</span>
        </button>

        {loading ? <span className="self-center text-sm text-[#927B68]">Loading stories...</span> : tray.filter((item) => item.author.id !== user?.id).map((item) => (
          <button key={item.author.id} type="button" onClick={() => void openStories(item.author.id)} className="flex flex-col items-center gap-2 min-w-[65px]">
            <span className={`flex w-16 h-16 items-center justify-center rounded-full text-white text-xl font-bold ring-4 ring-[#F5EBDD] outline outline-2 ${item.hasUnseen ? "outline-[#D87355] bg-[#8C6B85]" : "outline-[#D3BBA3] bg-[#637A9B]"}`}>
              {(item.author.fullName || item.author.username || "?").charAt(0).toUpperCase()}
            </span>
            <span className="max-w-[72px] truncate text-xs font-medium text-[#0F1B36]">{item.author.fullName || item.author.username}</span>
          </button>
        ))}
      </div>

      {composerOpen && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-[#0F1B36]/60 p-4">
          <div className="w-full max-w-lg rounded-3xl bg-[#F5EBDD] p-6 shadow-2xl">
            <div className="mb-5 flex items-center justify-between">
              <h3 className="text-xl font-bold">Create a story</h3>
              <button type="button" onClick={() => setComposerOpen(false)} aria-label="Close story composer"><X /></button>
            </div>
            <textarea value={storyText} onChange={(e) => setStoryText(e.target.value)} maxLength={500} rows={4} placeholder="Share a moment with your circle..." className="w-full resize-none rounded-xl border border-[#D3BBA3] bg-white p-3 outline-none focus:border-[#D87355]" />
            <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/gif,image/webp,video/mp4" onChange={(e) => setStoryFile(e.target.files?.[0] ?? null)} className="mt-4 block w-full text-sm" />
            {storyFile && <p className="mt-2 text-xs text-[#927B68]">{storyFile.name}</p>}
            <label className="mt-4 block text-sm font-semibold">Who can see this?</label>
            <select value={audience} onChange={(e) => setAudience(e.target.value as "PUBLIC" | "FRIENDS_ONLY")} className="mt-2 w-full rounded-xl border border-[#D3BBA3] bg-white p-3">
              <option value="FRIENDS_ONLY">Friends</option>
              <option value="PUBLIC">Everyone</option>
            </select>
            <button type="button" disabled={saving || (!storyText.trim() && !storyFile)} onClick={() => void submitStory()} className="mt-5 w-full rounded-xl bg-[#D87355] py-3 font-semibold text-white disabled:opacity-50">
              {saving ? "Posting..." : "Share story"}
            </button>
          </div>
        </div>
      )}

      {activeStories.length > 0 && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/90 p-4">
          <div className="relative flex h-[min(82vh,760px)] w-full max-w-md flex-col overflow-hidden rounded-2xl bg-[#0F1B36] text-white">
            <div className="absolute left-3 right-3 top-3 z-10 flex gap-1">
              {activeStories.map((story, index) => <span key={story.id} className="h-1 flex-1 rounded-full bg-white/30"><span className={`block h-full rounded-full ${index <= activeIndex ? "bg-white" : "bg-transparent"}`} /></span>)}
            </div>
            <button type="button" onClick={closeViewer} aria-label="Close story" className="absolute right-3 top-6 z-20 rounded-full bg-black/40 p-2"><X /></button>
            <div className="flex flex-1 items-center justify-center p-5">
              {viewerLoading ? <p>Loading story...</p> : mediaObjectUrl ? (
                activeStories[activeIndex].mediaType === "VIDEO" ? <video src={mediaObjectUrl} controls autoPlay className="max-h-full max-w-full" /> : <img src={mediaObjectUrl} alt="Story" className="max-h-full max-w-full object-contain" />
              ) : <p className="whitespace-pre-wrap text-center text-xl">{activeStories[activeIndex].text}</p>}
            </div>
            {activeStories[activeIndex].text && mediaObjectUrl && <p className="px-5 pb-3 text-center">{activeStories[activeIndex].text}</p>}
            <div className="flex items-center justify-between p-3">
              <button type="button" onClick={() => moveStory(-1)} className="rounded-full bg-white/10 p-2" aria-label="Previous story"><ChevronLeft /></button>
              <span className="text-xs text-white/70">{activeIndex + 1} / {activeStories.length}</span>
              <button type="button" onClick={() => moveStory(1)} className="rounded-full bg-white/10 p-2" aria-label="Next story"><ChevronRight /></button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
