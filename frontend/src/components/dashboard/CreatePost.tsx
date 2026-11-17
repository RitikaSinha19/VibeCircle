
import { useEffect, useRef, useState } from "react";
import { Image, Send, X } from "lucide-react";
import type { ChangeEvent } from "react";
interface CreatePostProps {
  onPost: (content: string, files: File[]) => Promise<void>;
  isPosting: boolean;
}

export default function CreatePost({
  onPost,
  isPosting,
}: CreatePostProps) {
  const [content, setContent] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const urls = files.map((file) => URL.createObjectURL(file));
    setPreviews(urls);

    return () => {
      urls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [files]);

  function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(event.target.files ?? []);

    setFiles((previous) => [...previous, ...selected].slice(0, 5));

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }

  function removeFile(index: number) {
    setFiles((previous) => previous.filter((_, i) => i !== index));
  }

  async function handleSubmit() {
    if ((!content.trim() && files.length === 0) || isPosting) return;

    try {
      await onPost(content, files);
      setContent("");
      setFiles([]);
    } catch {
      // The parent displays the API error.
    }
  }

  return (
    <div className="bg-[#F5EBDD] p-5 rounded-2xl shadow-sm mt-6">
      <div className="flex gap-4">
        <div className="w-11 h-11 shrink-0 rounded-full bg-[#0F1B36] text-white flex items-center justify-center font-bold">
          R
        </div>

        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder="What's happening in your circle?"
          rows={3}
          maxLength={2200}
          disabled={isPosting}
          className="flex-1 bg-transparent resize-none outline-none pt-2 placeholder:text-[#927B68] disabled:opacity-60"
        />
      </div>

      {previews.length > 0 && (
        <div className="flex flex-wrap gap-3 mt-4">
          {previews.map((preview, index) => (
            <div key={preview} className="relative">
              {files[index]?.type.startsWith("video/") ? (
                <video
                  src={preview}
                  controls
                  className="w-28 h-28 object-cover rounded-xl"
                />
              ) : (
                <img
                  src={preview}
                  alt="Post preview"
                  className="w-28 h-28 object-cover rounded-xl"
                />
              )}

              <button
                type="button"
                onClick={() => removeFile(index)}
                className="absolute -top-2 -right-2 bg-[#0F1B36] text-white rounded-full p-1"
                aria-label="Remove file"
              >
                <X size={14} />
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="flex justify-between items-center border-t border-[#D3BBA3] mt-4 pt-4">
        <div className="flex items-center gap-4 text-[#927B68]">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*,video/*"
            multiple
            hidden
            onChange={handleFiles}
          />

          <button
            type="button"
            title="Add images or videos"
            disabled={isPosting || files.length >= 5}
            onClick={() => fileInputRef.current?.click()}
            className="disabled:opacity-40"
          >
            <Image size={20} />
          </button>

          <span className="text-xs">
            {files.length}/5 files
          </span>
        </div>

        <button
          type="button"
          onClick={handleSubmit}
          disabled={
            isPosting || (!content.trim() && files.length === 0)
          }
          className="flex items-center gap-2 bg-[#D87355] text-white px-5 py-2.5 rounded-xl font-semibold disabled:opacity-40 hover:bg-[#bd6047] transition"
        >
          {isPosting ? "Posting..." : "Post"}
          <Send size={16} />
        </button>
      </div>
    </div>
  );
}
