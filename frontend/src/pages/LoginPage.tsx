
import { useState } from "react";
import { Eye, EyeOff, Mail, LockKeyhole } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";

import AuthLayout from "../components/layout/AuthLayout";
import { authApi } from "../api/authApi";
import useAuthStore from "../store/authStore";

export default function LoginPage() {
  const [showPassword, setShowPassword] = useState(false);
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();
  const setSession = useAuthStore((state) => state.setSession);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const response = await authApi.login({
        identifier: identifier.trim(),
        password,
      });

      setSession(response);
      navigate("/dashboard");
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        const data = err.response?.data as
          | { message?: string; detail?: string; error?: string }
          | undefined;

        setError(
          data?.message ||
          data?.detail ||
          data?.error ||
          "Login failed. Please check your credentials."
        );
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Welcome back!"
      subtitle="Your circle has been waiting for you."
    >
      <form className="space-y-6" onSubmit={handleSubmit}>
        <div>
          <label className="block text-sm font-semibold mb-2">
            Email or username
          </label>

          <div className="relative">
            <Mail
              className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
              size={19}
            />

            <input
              type="text"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="you@example.com or username"
              required
              autoComplete="username"
              className="w-full bg-white border border-[#E9D8C5] rounded-xl py-4 pl-12 pr-4 outline-none focus:border-[#D87355] transition"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-semibold mb-2">
            Password
          </label>

          <div className="relative">
            <LockKeyhole
              className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
              size={19}
            />

            <input
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              required
              autoComplete="current-password"
              className="w-full bg-white border border-[#E9D8C5] rounded-xl py-4 pl-12 pr-12 outline-none focus:border-[#D87355] transition"
            />

            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-[#927B68]"
              aria-label={showPassword ? "Hide password" : "Show password"}
            >
              {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
            </button>
          </div>
        </div>

        {error && (
          <p className="text-sm text-red-600 bg-red-50 rounded-xl p-3">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={loading}
          className="w-full bg-[#D87355] text-white py-4 rounded-xl font-semibold hover:bg-[#bd6047] hover:-translate-y-0.5 transition-all shadow-lg shadow-[#D87355]/20 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {loading ? "Logging in..." : "Log in to VibeCircle →"}
        </button>
      </form>

      <p className="text-center text-[#927B68] mt-9">
        New to VibeCircle?{" "}
        <Link
          to="/signup"
          className="text-[#D87355] font-bold hover:underline"
        >
          Create an account
        </Link>
      </p>
    </AuthLayout>
  );
}
