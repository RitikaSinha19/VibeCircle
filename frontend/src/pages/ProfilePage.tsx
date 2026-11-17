
import { useEffect, useRef, useState } from "react";
import {
  Camera,
  MapPin,
  Users,
  Heart,
  Grid3X3,
  Bookmark,
  Pencil,
  Settings,
  Plus,
} from "lucide-react";

import Sidebar from "../components/dashboard/Sidebar";
import { profileApi } from "../api/profileApi";
import type { ProfileResponse } from "../api/profileApi";
import useAuthStore from "../store/authStore";
import { profilePreferences } from "../api/profilePreferences";

const initialProfile = {
  name: "",
  username: "",
  bio: "",
  location: "Location not added",
  interests: [] as string[],
};

const profilePosts = [
  {
    id: 1,
    title: "Building something meaningful, one line at a time.",
    category: "Technology",
    image: "photo-1518770660439-4636190af475",
    height: "h-64",
    likes: 48,
  },
  {
    id: 2,
    title: "Finding inspiration in little things.",
    category: "Photography",
    image: "photo-1470252649378-9c29740c9fa8",
    height: "h-80",
    likes: 72,
  },
  {
    id: 3,
    title: "A creative mind never stops exploring.",
    category: "Art & Design",
    image: "photo-1513364776144-60967b0f800f",
    height: "h-56",
    likes: 35,
  },
];

type Tab = "posts" | "about" | "saved";

const INTEREST_OPTIONS = [
  "Art", "Sketching", "Photography", "Writing", "Music", "Dance", "Design", "Content creation",
  "Technology", "Coding", "AI & machine learning", "Startups", "Science", "Books", "Career growth", "Entrepreneurship",
  "Travel", "Food", "Fitness", "Fashion", "Movies", "Gaming", "Nature", "Personal growth",
];

export default function ProfilePage() {
  const user = useAuthStore((state) => state.user);
  const userId = user?.id;
  const [localCity, setLocalCity] = useState("");
  const [localInterests, setLocalInterests] = useState<string[]>([]);
  const [profile, setProfile] = useState(initialProfile);

  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);

  const [activeTab, setActiveTab] = useState<Tab>("posts");
  const [editing, setEditing] = useState(false);
  const [avatarPreview, setAvatarPreview] = useState<string>("");
  const avatarInputRef = useRef<HTMLInputElement>(null);

  const [savedPosts, setSavedPosts] = useState<number[]>([2]);

  // Convert backend response into the frontend profile structure.
  const applyProfileData = (data: ProfileResponse) => {
    setProfile({
      name: data.fullName,
      username: data.username,
      bio: data.bio ?? "",
      location: data.city
        ? [data.city.name, data.city.state, data.city.country]
            .filter(Boolean)
            .join(", ")
        : "Location not added",
      interests: data.interests.map((interest) => interest.name),
    });

    const saved = userId !== undefined && userId !== null
      ? profilePreferences.get(userId)
      : null;
    if (saved) {
      setLocalCity(saved.city);
      setLocalInterests(saved.interests);
      setProfile((previous) => ({
        ...previous,
        location: saved.city,
        interests: saved.interests,
      }));
    } else {
      setLocalCity(data.city?.name ?? "");
      setLocalInterests(data.interests.map((interest) => interest.name));
    }
  };

  // Fetch the logged-in user's profile.
  useEffect(() => {
    let cancelled = false;

    const loadProfile = async () => {
      try {
        setProfileLoading(true);
        setProfileError("");

        const data = await profileApi.getMyProfile();

        if (!cancelled) {
          applyProfileData(data);
        }
      } catch (error) {
        console.error("Profile loading failed:", error);

        if (!cancelled) {
          setProfileError(
            "Unable to load your profile. Please try again."
          );
        }
      } finally {
        if (!cancelled) {
          setProfileLoading(false);
        }
      }
    };

    loadProfile();

    return () => {
      cancelled = true;
    };
  }, []);

  // Update the bio in local form state.
  const updateBio = (value: string) => {
    setProfile((previous) => ({
      ...previous,
      bio: value,
    }));
  };

  // Save the profile through Spring Boot.
  const handleSaveProfile = async () => {
    setSavingProfile(true);
    setProfileError("");

    try {
      const updatedProfile = await profileApi.updateProfile({
        bio: profile.bio,
      });

      applyProfileData(updatedProfile);
      setEditing(false);
    } catch (error) {
      console.error("Profile update failed:", error);

      setProfileError(
        "Unable to save your profile. Please try again."
      );
    } finally {
      setSavingProfile(false);
    }
  };

  const toggleLocalInterest = (interest: string) => {
    setLocalInterests((current) => current.includes(interest)
      ? current.filter((item) => item !== interest)
      : [...current, interest]);
  };

  const openProfileEditor = () => {
    const saved = userId !== undefined && userId !== null
      ? profilePreferences.get(userId)
      : null;
    setLocalCity(saved?.city ?? (profile.location === "Location not added" ? "" : profile.location));
    setLocalInterests(saved?.interests ?? profile.interests);
    setEditing(true);
  };

  const handleSaveLocalPreferences = () => {
    if (userId === undefined || userId === null) {
      setProfileError("Your account could not be identified. Please log in again.");
      return;
    }
    if (!localCity.trim()) {
      setProfileError("Please enter your city.");
      return;
    }
    if (localInterests.length < 3) {
      setProfileError("Please select at least 3 interests.");
      return;
    }
    profilePreferences.save(userId, { city: localCity.trim(), interests: localInterests });
    setProfile((previous) => ({ ...previous, location: localCity.trim(), interests: localInterests }));
    setEditing(false);
    setProfileError("");
  };

  const visiblePosts =
    activeTab === "saved"
      ? profilePosts.filter((post) => savedPosts.includes(post.id))
      : profilePosts;

  return (
    <div className="min-h-screen bg-[#D3BBA3] flex text-[#0F1B36]">
      <Sidebar />

      <main className="flex-1 min-w-0 px-5 md:px-10 py-6 max-w-[1600px] mx-auto w-full">

        {/* HEADER */}
        <div className="flex items-center justify-between mb-7">
          <div>
            <p className="text-xs uppercase tracking-widest text-[#D87355] font-bold">
              Your personal space
            </p>

            <h1 className="text-3xl font-bold mt-2">
              My Profile
            </h1>
          </div>

          <button
            type="button"
            className="p-3 bg-[#F5EBDD] rounded-xl hover:bg-[#E9D8C5]"
            aria-label="Settings"
          >
            <Settings size={21} />
          </button>
        </div>

        {/* LOADING AND ERROR */}
        {profileLoading && (
          <div className="bg-[#F5EBDD] rounded-xl p-5 mb-6">
            Loading your profile...
          </div>
        )}

        {profileError && (
          <div className="bg-red-100 text-red-700 rounded-xl p-4 mb-6 flex items-center justify-between gap-4">
            <p>{profileError}</p>

            <button
              type="button"
              onClick={() => window.location.reload()}
              className="font-bold underline shrink-0"
            >
              Retry
            </button>
          </div>
        )}

        {/* COVER */}
        <section className="relative">
          <div className="h-48 md:h-64 rounded-3xl overflow-hidden bg-gradient-to-r from-[#0F1B36] via-[#594A63] to-[#D87355] relative">

            <div
              className="absolute inset-0 opacity-20"
              style={{
                backgroundImage:
                  "radial-gradient(circle at 20% 30%, white 1px, transparent 2px)",
                backgroundSize: "30px 30px",
              }}
            />

            <div className="absolute bottom-7 left-7 text-white">
              <p className="text-sm text-white/70">
                THIS IS MY CIRCLE
              </p>

              <h2 className="text-2xl md:text-4xl font-bold mt-2">
                Be yourself. Find your people.
              </h2>
            </div>

            <button
              type="button"
              aria-label="Change cover photo"
              className="absolute top-5 right-5 bg-[#0F1B36]/70 text-white p-3 rounded-full"
            >
              <Camera size={19} />
            </button>
          </div>

          {/* PROFILE IDENTITY */}
          <div className="bg-[#F5EBDD] rounded-3xl px-6 md:px-10 pb-8 mt-[-1px]">

            <div className="flex flex-col md:flex-row md:items-end justify-between gap-5">

              <div className="flex flex-col sm:flex-row sm:items-end gap-5">
                <div className="relative -mt-12 md:-mt-16">

                  <input
                    ref={avatarInputRef}
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    className="hidden"
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (!file) return;
                      if (file.size > 5 * 1024 * 1024) {
                        setProfileError("Choose a profile image smaller than 5 MB.");
                        event.target.value = "";
                        return;
                      }
                      const previewUrl = URL.createObjectURL(file);
                      setAvatarPreview((previous) => {
                        if (previous) URL.revokeObjectURL(previous);
                        return previewUrl;
                      });
                      setProfileError("Image preview updated. Permanent profile-picture upload needs a backend upload endpoint, which this project does not currently provide.");
                    }}
                  />
                  <div className="w-28 h-28 md:w-36 md:h-36 overflow-hidden rounded-full bg-[#D87355] border-[6px] border-[#F5EBDD] flex items-center justify-center text-white text-5xl font-bold">
                    {avatarPreview ? (
                      <img src={avatarPreview} alt="Profile preview" className="h-full w-full object-cover" />
                    ) : (
                      profile.name ? profile.name.charAt(0).toUpperCase() : "?"
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={() => avatarInputRef.current?.click()}
                    aria-label="Choose profile picture"
                    title="Choose profile picture"
                    className="absolute bottom-1 right-1 bg-[#0F1B36] text-white p-2 rounded-full hover:bg-[#24385D]"
                  >
                    <Camera size={15} />
                  </button>
                </div>

                <div className="pb-2">
                  <h2 className="text-2xl md:text-3xl font-bold">
                    {profileLoading ? "Loading..." : profile.name}
                  </h2>

                  <p className="text-[#927B68] mt-1">
                    @{profile.username || "username"}
                  </p>
                </div>
              </div>

              <div className="flex gap-3 pb-2">
                <button
                  type="button"
                  onClick={openProfileEditor}
                  disabled={profileLoading}
                  className="flex items-center gap-2 bg-[#0F1B36] text-white px-5 py-3 rounded-xl font-semibold hover:bg-[#24385D] transition disabled:opacity-50"
                >
                  <Pencil size={17} />
                  Edit Profile
                </button>


              </div>
            </div>

            {/* BIO */}
            <p className="max-w-2xl text-[#594A40] leading-relaxed mt-6 whitespace-pre-wrap">
              {profile.bio || "No bio added yet. Tell people something about yourself!"}
            </p>

            {/* LOCATION */}
            <div className="flex flex-wrap gap-5 mt-5 text-sm text-[#927B68]">
              <span className="flex items-center gap-2">
                <MapPin size={16} />
                {profile.location}
              </span>
            </div>

            {/* STATISTICS: PLACEHOLDER UNTIL STATS API IS CONNECTED */}
            <div className="grid grid-cols-3 gap-4 border-t border-[#D3BBA3] mt-8 pt-7 max-w-xl">
              {[
                { label: "Posts", value: "12" },
                { label: "Connections", value: "248" },
                { label: "Communities", value: "8" },
              ].map((stat) => (
                <div key={stat.label}>
                  <p className="text-2xl font-bold">
                    {stat.value}
                  </p>

                  <p className="text-sm text-[#927B68] mt-1">
                    {stat.label}
                  </p>
                </div>
              ))}
            </div>

            {/* INTERESTS */}
            <div className="mt-8">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <h3 className="font-bold">My Interests</h3>
                <button type="button" onClick={openProfileEditor} className="inline-flex items-center gap-1 rounded-full bg-[#D87355]/10 px-3 py-2 text-xs font-bold text-[#D87355] hover:bg-[#D87355]/20">
                  <Pencil size={13} /> Edit interests
                </button>
              </div>

              <div className="flex flex-wrap gap-3">
                {profile.interests.length > 0 ? (
                  profile.interests.map((interest) => (
                    <span
                      key={interest}
                      className="bg-[#E9D8C5] text-[#594A40] px-4 py-2 rounded-full text-sm font-medium"
                    >
                      {interest}
                    </span>
                  ))
                ) : (
                  <p className="text-sm text-[#927B68]">
                    No interests added yet.
                  </p>
                )}

                <button
                  type="button"
                  onClick={openProfileEditor}
                  className="bg-[#D87355]/10 text-[#D87355] px-4 py-2 rounded-full text-sm font-semibold flex items-center gap-1"
                >
                  <Plus size={15} />
                  Add interests
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* TABS */}
        <section className="mt-10">
          <div className="flex gap-8 border-b border-[#0F1B36]/15">
            {[
              { id: "posts" as Tab, label: "Posts", icon: Grid3X3 },
              { id: "about" as Tab, label: "About", icon: Users },
              { id: "saved" as Tab, label: "Saved", icon: Bookmark },
            ].map((tab) => {
              const Icon = tab.icon;

              return (
                <button
                  type="button"
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`flex items-center gap-2 pb-4 font-semibold border-b-2 transition ${
                    activeTab === tab.id
                      ? "border-[#D87355] text-[#D87355]"
                      : "border-transparent text-[#927B68]"
                  }`}
                >
                  <Icon size={18} />
                  {tab.label}
                </button>
              );
            })}
          </div>

          {/* POSTS: PLACEHOLDER UNTIL FEED API IS CONNECTED */}
          {activeTab === "posts" && (
            <div className="columns-1 sm:columns-2 xl:columns-3 gap-5 mt-7">
              {visiblePosts.map((post) => (
                <article
                  key={post.id}
                  className="break-inside-avoid mb-5 bg-[#F5EBDD] rounded-2xl overflow-hidden shadow-sm"
                >
                  <img
                    src={`https://images.unsplash.com/${post.image}?auto=format&fit=crop&w=700&q=80`}
                    alt={post.title}
                    className={`w-full ${post.height} object-cover`}
                    loading="lazy"
                  />

                  <div className="p-5">
                    <span className="text-xs text-[#D87355] font-semibold">
                      {post.category}
                    </span>

                    <h3 className="font-bold mt-2">
                      {post.title}
                    </h3>

                    <div className="flex justify-between mt-5 text-[#927B68]">
                      <span className="flex items-center gap-2 text-sm">
                        <Heart size={17} />
                        {post.likes}
                      </span>

                      <button
                        type="button"
                        onClick={() =>
                          setSavedPosts((previous) =>
                            previous.includes(post.id)
                              ? previous.filter((id) => id !== post.id)
                              : [...previous, post.id]
                          )
                        }
                        aria-label="Toggle saved post"
                      >
                        <Bookmark
                          size={19}
                          className={
                            savedPosts.includes(post.id)
                              ? "text-[#D87355]"
                              : ""
                          }
                          fill={
                            savedPosts.includes(post.id)
                              ? "currentColor"
                              : "none"
                          }
                        />
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}

          {/* ABOUT */}
          {activeTab === "about" && (
            <div className="bg-[#F5EBDD] rounded-2xl p-7 mt-7 max-w-3xl">
              <h3 className="text-xl font-bold mb-5">
                A little about me
              </h3>

              <p className="text-[#594A40] leading-relaxed whitespace-pre-wrap">
                {profile.bio || "No bio added yet."}
              </p>

              <div className="mt-7 space-y-4 text-sm">
                <p>
                  <strong>Full name:</strong> {profile.name}
                </p>

                <p>
                  <strong>Username:</strong> @{profile.username}
                </p>

                <p>
                  <strong>Location:</strong> {profile.location}
                </p>

                <p>
                  <strong>Interests:</strong>{" "}
                  {profile.interests.length > 0
                    ? profile.interests.join(", ")
                    : "None added"}
                </p>
              </div>
            </div>
          )}

          {/* SAVED */}
          {activeTab === "saved" && (
            <div className="mt-7">
              {visiblePosts.length === 0 ? (
                <div className="bg-[#F5EBDD] p-12 rounded-2xl text-center">
                  <Bookmark
                    size={32}
                    className="mx-auto text-[#D87355]"
                  />

                  <h3 className="font-bold text-xl mt-4">
                    No saved posts yet
                  </h3>

                  <p className="text-[#927B68] mt-2">
                    Save something inspiring to find it here.
                  </p>
                </div>
              ) : (
                <div className="columns-1 sm:columns-2 xl:columns-3 gap-5">
                  {visiblePosts.map((post) => (
                    <article
                      key={post.id}
                      className="break-inside-avoid mb-5 bg-[#F5EBDD] rounded-2xl overflow-hidden"
                    >
                      <img
                        src={`https://images.unsplash.com/${post.image}?auto=format&fit=crop&w=700&q=80`}
                        alt={post.title}
                        className={`w-full ${post.height} object-cover`}
                      />

                      <div className="p-4 font-semibold">
                        {post.title}
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          )}
        </section>

        {/* EDIT PROFILE MODAL */}
        {editing && (
          <div className="fixed inset-0 z-50 bg-[#0F1B36]/60 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-[#F5EBDD] w-full max-w-lg rounded-3xl p-7 max-h-[90vh] overflow-y-auto">

              <div className="flex justify-between items-center mb-7">
                <h2 className="text-2xl font-bold">
                  Edit your profile
                </h2>

                <button
                  type="button"
                  onClick={() => setEditing(false)}
                  className="text-[#927B68] text-2xl"
                  aria-label="Close editor"
                >
                  ×
                </button>
              </div>

              <div>
                <label className="block font-semibold text-sm mb-2">
                  About you
                </label>

                <textarea
                  value={profile.bio}
                  onChange={(event) => updateBio(event.target.value)}
                  rows={5}
                  maxLength={300}
                  placeholder="Tell people something about yourself..."
                  className="w-full bg-white border border-[#D3BBA3] rounded-xl p-3 outline-none focus:border-[#D87355] resize-none"
                />

                <p className="text-xs text-[#927B68] mt-2">
                  {profile.bio.length}/300 characters
                </p>
              </div>

              <div className="mt-6">
                <label className="mb-2 block font-semibold text-sm">City</label>
                <input value={localCity} onChange={(event) => setLocalCity(event.target.value)} maxLength={100} placeholder="e.g. Kolkata, West Bengal" className="w-full bg-white border border-[#D3BBA3] rounded-xl p-3 outline-none focus:border-[#D87355]" />
              </div>

              <div className="mt-6">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <label className="block font-semibold text-sm">Your interests</label>
                  <span className="text-xs text-[#927B68]">{localInterests.length} selected · minimum 3</span>
                </div>
                <div className="flex max-h-52 flex-wrap gap-2 overflow-y-auto">
                  {INTEREST_OPTIONS.map((interest) => {
                    const selected = localInterests.includes(interest);
                    return <button key={interest} type="button" aria-pressed={selected} onClick={() => toggleLocalInterest(interest)} className={`rounded-full border px-3 py-2 text-xs font-semibold transition ${selected ? "border-[#D87355] bg-[#D87355] text-white" : "border-[#D3BBA3] bg-white text-[#594A40] hover:border-[#D87355]"}`}>{interest}</button>;
                  })}
                </div>
                <p className="mt-2 text-xs leading-5 text-[#927B68]">City and interests are saved in this browser for your account. They are not synced to the server.</p>
              </div>

              <button type="button" onClick={handleSaveProfile} disabled={savingProfile} className="w-full rounded-xl border border-[#0F1B36] py-3 font-semibold text-[#0F1B36] mt-6 hover:bg-[#0F1B36]/5 disabled:opacity-60">
                {savingProfile ? "Saving bio..." : "Save bio"}
              </button>
              <button type="button" onClick={handleSaveLocalPreferences} className="w-full bg-[#D87355] text-white font-semibold py-4 rounded-xl mt-3 hover:bg-[#bd6047] transition">
                Save preferences
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
