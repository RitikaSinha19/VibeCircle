
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowRight, Check, MapPin, Sparkles } from "lucide-react";

import useAuthStore from "../store/authStore";
import { profileApi } from "../api/profileApi";
import { profilePreferences } from "../api/profilePreferences";

const CITIES = [
  { id: 1, name: "Bengaluru" },
  { id: 2, name: "Kolkata" },
  { id: 3, name: "Mumbai" },
  { id: 4, name: "Delhi" },
  { id: 5, name: "Ranchi" },
  { id: 6, name: "Chennai" },
  { id: 7, name: "Hyderabad" },
];

const INTERESTS = [
  { id: 1, name: "Cricket" },
  { id: 2, name: "Football" },
  { id: 3, name: "Badminton" },
  { id: 4, name: "Basketball" },
  { id: 5, name: "Swimming" },
  { id: 6, name: "Sketching" },
  { id: 7, name: "Painting" },
  { id: 8, name: "Photography" },
  { id: 9, name: "Design" },
  { id: 10, name: "Singing" },
  { id: 11, name: "Dancing" },
  { id: 12, name: "Instruments" },
  { id: 13, name: "Music Production" },
  { id: 14, name: "Programming" },
  { id: 15, name: "AI" },
];

export default function ProfileSetupPage() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const userId = user?.id;

  const [cityId, setCityId] = useState<number | "">("");
  const [selectedInterests, setSelectedInterests] = useState<number[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (userId === undefined || userId === null) {
      navigate("/login", { replace: true });
      return;
    }

    let active = true;

    async function loadProfile() {
      try {
        const profile = await profileApi.getMyProfile();

        if (!active) return;

        setCityId(profile.city?.id ?? "");
        setSelectedInterests(profile.interests.map((item) => item.id));
      } catch (err) {
        console.error("Could not load profile:", err);
        if (active) {
          setError("Could not load your profile. Please refresh and try again.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadProfile();

    return () => {
      active = false;
    };
  }, [userId, navigate]);

  function toggleInterest(id: number) {
    setSelectedInterests((current) =>
      current.includes(id)
        ? current.filter((item) => item !== id)
        : [...current, id]
    );
    setError("");
  }

  async function handleContinue(
    event: React.FormEvent<HTMLFormElement>
  ) {
    event.preventDefault();
    setError("");

    if (cityId === "") {
      setError("Please select your city.");
      return;
    }

    if (selectedInterests.length < 3) {
      setError("Please choose at least 3 interests.");
      return;
    }

    if (userId === undefined || userId === null) {
      setError("Please log in again.");
      return;
    }

    setSaving(true);

    try {
      // Save city and discovery settings to the backend.
      await profileApi.updateProfile({
        cityId,
        profileVisibility: "PUBLIC",
        discoverable: true,
        friendRequestPolicy: "EVERYONE",
      });

      // Save actual database interest IDs to the backend.
      await profileApi.updateInterests(selectedInterests);

// Verify backend saved the profile.
const savedProfile = await profileApi.getMyProfile();

if (
  !savedProfile.city ||
  savedProfile.interests.length === 0 ||
  !savedProfile.profileComplete
) {
  throw new Error("Profile was not saved completely. Please try again.");
}

// Keep local preferences for existing frontend components.

      // Keep local preferences for existing frontend components.
      const selectedCity = CITIES.find((city) => city.id === cityId);

      profilePreferences.save(userId, {
        city: selectedCity?.name ?? "",
        interests: INTERESTS
          .filter((interest) => selectedInterests.includes(interest.id))
          .map((interest) => interest.name),
      });

      navigate("/dashboard", { replace: true });
    } catch (err) {
      console.error("Profile setup failed:", err);
      setError(
        "Could not save your discovery profile. Please check your connection and try again."
      );
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#F5EBDD] text-[#0F1B36]">
        Loading your profile...
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#F5EBDD] px-4 py-8 text-[#0F1B36] md:px-8">
      <div className="mx-auto max-w-4xl">
        <header className="mb-10 flex items-center justify-between">
          <div>
            <p className="text-xl font-black tracking-tight">
              VibeCircle<span className="text-[#D87355]">.</span>
            </p>
            <p className="mt-1 text-xs font-semibold uppercase tracking-[0.2em] text-[#927B68]">
              Find Your People. Find Your Vibe.
            </p>
          </div>
          <span className="rounded-full bg-white px-4 py-2 text-xs font-bold text-[#927B68]">
            YOUR PROFILE · 01/01
          </span>
        </header>

        <main className="overflow-hidden rounded-[2rem] bg-white shadow-xl shadow-[#0F1B36]/5">
          <div className="bg-[#0F1B36] px-7 py-9 text-white md:px-12 md:py-12">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1.5 text-xs font-semibold text-[#E9A58D]">
              <Sparkles size={14} /> A little more about you
            </div>

            <h1 className="max-w-2xl text-3xl font-black leading-tight md:text-5xl">
              Your vibe deserves{" "}
              <span className="text-[#E9A58D]">its own space.</span>
            </h1>

            <p className="mt-4 max-w-xl text-sm leading-6 text-white/70 md:text-base">
              Tell us where you're based and what you love. This helps you
              discover people with shared interests.
            </p>
          </div>

          <form
            onSubmit={handleContinue}
            className="space-y-9 px-6 py-8 md:px-12 md:py-10"
          >
            <section>
              <div className="mb-3 flex items-center gap-2">
                <MapPin className="text-[#D87355]" size={19} />
                <h2 className="text-lg font-extrabold">
                  Where are you based?
                </h2>
              </div>

              <p className="mb-4 text-sm text-[#927B68]">
                Select your city from the available locations.
              </p>

              <select
                value={cityId}
                onChange={(event) =>
                  setCityId(event.target.value ? Number(event.target.value) : "")
                }
                required
                className="w-full rounded-2xl border border-[#E9D8C5] bg-[#FFFCF8] px-5 py-4 text-sm outline-none focus:border-[#D87355]"
              >
                <option value="">Select your city</option>
                {CITIES.map((city) => (
                  <option key={city.id} value={city.id}>
                    {city.name}
                  </option>
                ))}
              </select>
            </section>

            <section>
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <h2 className="text-lg font-extrabold">
                  What are you into?
                </h2>

                <span className="rounded-full bg-[#F5EBDD] px-3 py-1 text-xs font-bold text-[#927B68]">
                  {selectedInterests.length} selected · choose at least 3
                </span>
              </div>

              <p className="mb-6 text-sm text-[#927B68]">
                Select your interests. These are saved to your VibeCircle
                account.
              </p>

              <div className="flex flex-wrap gap-2.5">
                {INTERESTS.map((interest) => {
                  const selected = selectedInterests.includes(interest.id);

                  return (
                    <button
                      key={interest.id}
                      type="button"
                      aria-pressed={selected}
                      onClick={() => toggleInterest(interest.id)}
                      className={`inline-flex items-center gap-2 rounded-full border px-4 py-2.5 text-sm font-semibold transition ${
                        selected
                          ? "border-[#D87355] bg-[#D87355] text-white shadow-md"
                          : "border-[#E9D8C5] bg-white text-[#594A3F] hover:border-[#D87355]"
                      }`}
                    >
                      {selected && <Check size={14} strokeWidth={3} />}
                      {interest.name}
                    </button>
                  );
                })}
              </div>
            </section>

            <div className="rounded-xl bg-[#F5EBDD] p-4 text-sm text-[#594A3F]">
              Your profile will be public and discoverable, and other users
              will be able to send you friend requests.
            </div>

            {error && (
              <p
                role="alert"
                className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700"
              >
                {error}
              </p>
            )}

            <div className="flex flex-col-reverse items-stretch justify-between gap-3 border-t border-[#F0E5D8] pt-6 sm:flex-row sm:items-center">
              <p className="max-w-md text-xs leading-5 text-[#927B68]">
                Your city, interests and discovery settings will be saved to
                your account.
              </p>

              <button
                type="submit"
                disabled={saving}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-[#D87355] px-7 py-4 text-sm font-bold text-white shadow-lg transition hover:bg-[#bd6047] disabled:cursor-not-allowed disabled:opacity-60"
              >
                {saving ? "Saving profile..." : "Save and enter VibeCircle"}
                {!saving && <ArrowRight size={17} />}
              </button>
            </div>
          </form>
        </main>
      </div>
    </div>
  );
}
