
import { ArrowLeft, Sparkles, Users, Heart } from "lucide-react";
import { Link } from "react-router-dom";

interface AuthLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle: string;
}

export default function AuthLayout({
  children,
  title,
  subtitle,
}: AuthLayoutProps) {
  return (
    <div className="min-h-screen bg-[#F5EBDD] grid lg:grid-cols-2">

      {/* LEFT PANEL */}
      <div className="hidden lg:flex bg-[#0F1B36] text-white p-14 flex-col justify-between relative overflow-hidden">

        <Link to="/" className="text-3xl font-bold">
          Vibe<span className="text-[#D87355]">Circle.</span>
        </Link>

        <div className="relative z-10 max-w-lg">
          <div className="bg-white/10 w-fit p-4 rounded-2xl mb-8">
            <Sparkles className="text-[#E9A58D]" size={32} />
          </div>

          <h1 className="text-5xl font-bold leading-tight">
            Your people.
            <br />
            Your passions.
            <br />
            <span className="text-[#D87355]">Your circle.</span>
          </h1>

          <p className="text-white/65 text-lg mt-6 leading-relaxed">
            Discover communities, share your interests
            and connect with people who understand your vibe.
          </p>

          <div className="flex gap-4 mt-10">
            <div className="bg-white/10 p-4 rounded-2xl">
              <Users className="text-[#E9A58D]" />
              <p className="mt-3 font-semibold">Connect</p>
            </div>

            <div className="bg-white/10 p-4 rounded-2xl">
              <Heart className="text-[#D87355]" />
              <p className="mt-3 font-semibold">Belong</p>
            </div>
          </div>
        </div>

        <p className="text-white/40 text-sm">
          © 2026 VibeCircle. Find your people.
        </p>

        <div className="absolute w-96 h-96 bg-[#D87355]/10 rounded-full blur-3xl -bottom-40 -right-20" />
      </div>

      {/* RIGHT PANEL */}
      <div className="flex items-center justify-center p-6 md:p-12">

        <div className="w-full max-w-md">

          <Link to="/" className="inline-flex items-center gap-2 text-[#927B68] hover:text-[#D87355] mb-10">
            <ArrowLeft size={18} />
            Back to home
          </Link>

          <div className="lg:hidden text-3xl font-bold mb-10">
            Vibe<span className="text-[#D87355]">Circle.</span>
          </div>

          <h2 className="text-4xl font-bold text-[#0F1B36]">
            {title}
          </h2>

          <p className="text-[#927B68] mt-3 mb-9">
            {subtitle}
          </p>

          {children}

        </div>
      </div>
    </div>
  );
}
