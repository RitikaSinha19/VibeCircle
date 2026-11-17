
import { useState } from "react";
import {
  UserRound,
  Mail,
  LockKeyhole,
  Eye,
  EyeOff,
  AtSign,
  CalendarDays,
  ShieldCheck,
  ArrowLeft,
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";

import AuthLayout from "../components/layout/AuthLayout";
import { authApi } from "../api/authApi";

export default function SignupPage() {
  const [showPassword, setShowPassword] = useState(false);

  const [fullName, setFullName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [password, setPassword] = useState("");

  const [otp, setOtp] = useState("");
  const [verificationStep, setVerificationStep] = useState(false);
  const [verified, setVerified] = useState(false);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();

  // Calculate the latest date of birth allowed for an 18-year-old.
  const today = new Date();

  const maxDob = new Date(
    today.getFullYear() - 18,
    today.getMonth(),
    today.getDate()
  );

  const maxDobString = [
    maxDob.getFullYear(),
    String(maxDob.getMonth() + 1).padStart(2, "0"),
    String(maxDob.getDate()).padStart(2, "0"),
  ].join("-");

  const getErrorMessage = (err: unknown, fallback: string) => {
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as
        | { message?: string; detail?: string; error?: string }
        | undefined;

      return (
        data?.message ||
        data?.detail ||
        data?.error ||
        fallback
      );
    }

    return fallback;
  };

  // --------------------------------------------------
  // REGISTER
  // --------------------------------------------------

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    setError("");
    setSuccess("");

    if (!/^[A-Za-z0-9_.]{3,30}$/.test(username.trim())) {
      setError(
        "Username must contain 3-30 characters using letters, numbers, dots or underscores."
      );
      return;
    }

    setLoading(true);

    try {
      const response = await authApi.register({
        fullName: fullName.trim(),
        username: username.trim(),
        email: email.trim(),
        password,
        dateOfBirth,
      });

      // Backend sends this message when email verification is enabled.
      const otpRequired = response.message
        .toLowerCase()
        .includes("otp");

      if (otpRequired) {
        setVerificationStep(true);
        setSuccess(response.message);
      } else {
        setVerified(true);
        setSuccess(response.message || "Account created successfully!");
      }
    } catch (err: unknown) {
      setError(
        getErrorMessage(
          err,
          "Registration failed. Please check your details."
        )
      );
    } finally {
      setLoading(false);
    }
  };

  // --------------------------------------------------
  // VERIFY OTP
  // --------------------------------------------------

  const handleVerifyOtp = async (
    e: React.FormEvent<HTMLFormElement>
  ) => {
    e.preventDefault();

    setError("");
    setSuccess("");

    if (!/^[0-9]{6}$/.test(otp)) {
      setError("Please enter a valid 6-digit OTP.");
      return;
    }

    setLoading(true);

    try {
      await authApi.verifyEmail({
        username: username.trim(),
        otp,
      });

      setVerified(true);
      setSuccess("Your email has been verified successfully!");
    } catch (err: unknown) {
      setError(
        getErrorMessage(
          err,
          "OTP verification failed. Please try again."
        )
      );
    } finally {
      setLoading(false);
    }
  };

  const inputClass =
    "w-full bg-white border border-[#E9D8C5] rounded-xl py-4 pl-12 pr-4 outline-none focus:border-[#D87355] transition";

  // --------------------------------------------------
  // VERIFICATION SUCCESS SCREEN
  // --------------------------------------------------

  if (verified) {
    return (
      <AuthLayout
        title="You're in!"
        subtitle="Your VibeCircle account is ready."
      >
        <div className="text-center space-y-6">
          <div className="mx-auto w-20 h-20 rounded-full bg-green-50 flex items-center justify-center">
            <ShieldCheck size={42} className="text-green-600" />
          </div>

          <div>
            <h2 className="text-xl font-bold text-[#3D3028]">
              Account successfully created
            </h2>

            <p className="text-sm text-[#927B68] mt-2">
              {success}
            </p>
          </div>

          <button
            type="button"
            onClick={() => navigate("/login")}
            className="w-full bg-[#D87355] text-white py-4 rounded-xl font-semibold hover:bg-[#bd6047] transition"
          >
            Continue to login →
          </button>
        </div>
      </AuthLayout>
    );
  }

  // --------------------------------------------------
  // OTP VERIFICATION SCREEN
  // --------------------------------------------------

  if (verificationStep) {
    return (
      <AuthLayout
        title="Verify your email."
        subtitle="One last step before joining your circle."
      >
        <form className="space-y-5" onSubmit={handleVerifyOtp}>
          <div className="text-center py-4">
            <div className="mx-auto w-16 h-16 rounded-full bg-[#FDF0E9] flex items-center justify-center mb-4">
              <Mail size={30} className="text-[#D87355]" />
            </div>

            <h2 className="text-lg font-bold text-[#3D3028]">
              Check your inbox
            </h2>

            <p className="text-sm text-[#927B68] mt-2">
              We've sent a 6-digit verification code to:
            </p>

            <p className="font-semibold text-[#D87355] mt-1">
              {email}
            </p>

            <p className="text-xs text-[#927B68] mt-2">
              Account: @{username}
            </p>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-2">
              Enter verification code
            </label>

            <input
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={otp}
              onChange={(e) =>
                setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))
              }
              placeholder="000000"
              maxLength={6}
              required
              className="w-full bg-white border border-[#E9D8C5] rounded-xl py-5 px-4 text-center text-2xl tracking-[0.6em] font-bold outline-none focus:border-[#D87355] transition"
            />

            <p className="text-xs text-[#927B68] mt-2">
              Your OTP is valid for 10 minutes.
            </p>
          </div>

          {error && (
            <p className="text-sm text-red-600 bg-red-50 rounded-xl p-3">
              {error}
            </p>
          )}

          {success && (
            <p className="text-sm text-green-700 bg-green-50 rounded-xl p-3">
              {success}
            </p>
          )}

          <button
            type="submit"
            disabled={loading || otp.length !== 6}
            className="w-full bg-[#D87355] text-white py-4 rounded-xl font-semibold hover:bg-[#bd6047] transition-all shadow-lg shadow-[#D87355]/20 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {loading ? "Verifying..." : "Verify my email →"}
          </button>

          <button
            type="button"
            onClick={() => {
              setVerificationStep(false);
              setOtp("");
              setError("");
              setSuccess("");
            }}
            className="w-full flex items-center justify-center gap-2 text-sm text-[#927B68] hover:text-[#D87355] transition"
          >
            <ArrowLeft size={16} />
            Back to signup
          </button>
        </form>
      </AuthLayout>
    );
  }

  // --------------------------------------------------
  // SIGNUP SCREEN
  // --------------------------------------------------

  return (
    <AuthLayout
      title="Join your circle."
      subtitle="Create an account and find people who share your vibe."
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div>
          <label className="block text-sm font-semibold mb-2">
            Full name
          </label>

          <div className="relative">
            <UserRound
              className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
              size={19}
            />

            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Your full name"
              required
              maxLength={100}
              autoComplete="name"
              className={inputClass}
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-semibold mb-2">
            Username
          </label>

          <div className="relative">
            <AtSign
              className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
              size={19}
            />

            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Choose a username"
              required
              minLength={3}
              maxLength={30}
              autoComplete="username"
              className={inputClass}
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-semibold mb-2">
            Email address
          </label>

          <div className="relative">
            <Mail
              className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
              size={19}
            />

            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              maxLength={255}
              autoComplete="email"
              className={inputClass}
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-semibold mb-2">
            Date of birth
          </label>

          <div className="relative">
            <CalendarDays
              className="absolute left-4 top-1/2 -translate-y-1/2 text-[#927B68]"
              size={19}
            />

            <input
              type="date"
              value={dateOfBirth}
              onChange={(e) => setDateOfBirth(e.target.value)}
              max={maxDobString}
              required
              className={inputClass}
            />
          </div>

          <p className="text-xs text-[#927B68] mt-2">
            You must be at least 18 years old to join.
          </p>
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
              placeholder="Create a password"
              required
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
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

          <p className="text-xs text-[#927B68] mt-2">
            Use at least 8 characters.
          </p>
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
          {loading ? "Creating account..." : "Create my account →"}
        </button>
      </form>

      <p className="text-center text-[#927B68] mt-8">
        Already have an account?{" "}
        <Link
          to="/login"
          className="text-[#D87355] font-bold hover:underline"
        >
          Log in
        </Link>
      </p>
    </AuthLayout>
  );
}
