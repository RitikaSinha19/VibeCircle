
import { ArrowRight, Users, Compass, Heart, MessageCircle } from "lucide-react";
import { Link } from "react-router-dom";

const interests = [
  "🎨 Art & Design",
  "💻 Technology",
  "🎵 Music",
  "📸 Photography",
  "📚 Books",
  "🎮 Gaming",
  "✈️ Travel",
  "🌱 Lifestyle",
];

export default function LandingPage() {
  return (
    <main className="min-h-screen bg-[#D3BBA3] text-[#0F1B36]">

      {/* NAVBAR */}
      <nav className="flex items-center justify-between px-6 md:px-16 py-6">
        <h1 className="text-3xl font-bold tracking-tight">
          Vibe<span className="text-[#D87355]">Circle.</span>
        </h1>

        <div className="hidden md:flex gap-10 font-medium">
          <a href="#about" className="hover:text-[#D87355]">About</a>
          <a href="#interests" className="hover:text-[#D87355]">Explore</a>
          <a href="#communities" className="hover:text-[#D87355]">Communities</a>
        </div>

        <div className="flex gap-3">
          <Link
  to="/login"
  className="font-semibold px-4 py-2"
>
  Log in
</Link>
          <Link
  to="/signup"
  className="bg-[#D87355] text-white px-5 py-2.5 rounded-xl font-semibold hover:bg-[#bd6047] transition"
>
  Join VibeCircle
</Link>
        </div>
      </nav>

      {/* HERO */}
      <section className="px-6 md:px-16 py-20 md:py-28 grid md:grid-cols-2 gap-16 items-center">

        <div>
          <span className="inline-block bg-[#F5EBDD] px-5 py-2 rounded-full text-sm font-semibold mb-8">
            ✨ Your interests. Your people. Your circle.
          </span>

          <h2 className="text-5xl md:text-7xl font-bold leading-tight">
            Find Your
            <br />
            People.
            <br />
            Find Your
            <br />
            <span className="text-[#D87355]">Vibe.</span>
          </h2>

          <p className="text-lg text-[#594A40] mt-7 max-w-lg leading-relaxed">
            Discover people who share your interests,
            explore communities and create meaningful
            connections. Your next favourite circle
            is waiting for you.
          </p>

          <div className="flex flex-wrap gap-4 mt-9">
            <Link
  to="/signup"
  className="bg-[#0F1B36] text-white px-7 py-4 rounded-xl flex items-center gap-3 hover:bg-[#24385D] transition"
>
  Find Your Circle <ArrowRight size={19} />
</Link>

            <Link
  to="/communities"
  className="border-2 border-[#0F1B36] px-7 py-4 rounded-xl font-semibold hover:bg-[#F5EBDD] transition"
>
  Explore Communities
</Link>
          </div>

          <div className="flex gap-8 mt-12">
            <div>
              <h3 className="text-2xl font-bold">Your</h3>
              <p className="text-sm text-[#594A40]">Interests</p>
            </div>
            <div>
              <h3 className="text-2xl font-bold">Your</h3>
              <p className="text-sm text-[#594A40]">Community</p>
            </div>
            <div>
              <h3 className="text-2xl font-bold">Your</h3>
              <p className="text-sm text-[#594A40]">People</p>
            </div>
          </div>
        </div>

        {/* HERO VISUAL */}
        <div className="relative">
          <div className="bg-[#0F1B36] rounded-[32px] p-8 md:p-12 text-white shadow-2xl">
            <div className="flex justify-between items-center mb-12">
              <div>
                <p className="text-[#E9A58D] text-sm">YOUR NEXT CIRCLE</p>
                <h3 className="text-3xl font-bold mt-2">People Like You.</h3>
              </div>
              <Users size={35} className="text-[#D87355]" />
            </div>

            <div className="grid grid-cols-2 gap-4">
              {interests.slice(0, 4).map((interest) => (
                <div
                  key={interest}
                  className="bg-white/10 rounded-2xl p-5 hover:bg-white/20 transition"
                >
                  <p className="text-lg">{interest}</p>
                </div>
              ))}
            </div>

            <div className="mt-8 bg-[#D87355] rounded-2xl p-6">
              <p className="font-semibold text-xl">
                Your people are out there.
              </p>
              <p className="text-sm mt-2 text-white/90">
                Start discovering your circle today.
              </p>
            </div>
          </div>

          <div className="absolute -top-5 -right-4 bg-[#F5EBDD] p-4 rounded-2xl shadow-lg">
            <Heart className="text-[#D87355]" size={25} />
          </div>
        </div>
      </section>

      {/* INTERESTS */}
      <section id="interests" className="bg-[#F5EBDD] px-6 md:px-16 py-24">
        <div className="text-center max-w-2xl mx-auto">
          <p className="text-[#D87355] font-bold tracking-widest text-sm">
            EXPLORE YOUR INTERESTS
          </p>

          <h2 className="text-4xl md:text-5xl font-bold mt-4">
            There's a circle for every vibe.
          </h2>

          <p className="text-[#927B68] mt-5">
            From creative minds to curious explorers,
            find people who love what you love.
          </p>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-5 mt-14 max-w-5xl mx-auto">
          {interests.map((interest) => (
            <div
              key={interest}
              className="bg-white p-7 rounded-2xl text-center font-semibold shadow-sm hover:-translate-y-2 hover:shadow-lg transition"
            >
              {interest}
            </div>
          ))}
        </div>
      </section>

      {/* ABOUT */}
      <section id="about" className="px-6 md:px-16 py-24">
        <div className="max-w-5xl mx-auto grid md:grid-cols-3 gap-8">
          {[
            {
              icon: Compass,
              title: "Discover",
              description: "Explore people and communities that match your interests.",
            },
            {
              icon: Users,
              title: "Connect",
              description: "Build friendships with people who understand your vibe.",
            },
            {
              icon: MessageCircle,
              title: "Belong",
              description: "Share ideas, conversations and experiences together.",
            },
          ].map((item) => (
            <div key={item.title} className="bg-[#F5EBDD] p-8 rounded-3xl">
              <item.icon size={35} className="text-[#D87355]" />
              <h3 className="text-2xl font-bold mt-6">{item.title}</h3>
              <p className="text-[#927B68] mt-3 leading-relaxed">
                {item.description}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section id="communities" className="bg-[#0F1B36] text-white px-6 py-24 text-center">
        <h2 className="text-4xl md:text-6xl font-bold">
          Your circle starts here.
        </h2>

        <p className="text-white/70 mt-5">
          Find your people. Share your passions. Build your circle.
        </p>

        <Link
  to="/signup"
  className="inline-block mt-9 bg-[#D87355] px-8 py-4 rounded-xl font-semibold hover:bg-[#bd6047] transition"
>
  Join VibeCircle <ArrowRight className="inline ml-2" size={19} />
</Link>
      </section>

      {/* FOOTER */}
      <footer className="bg-[#0F1B36] border-t border-white/10 text-white/60 px-6 md:px-16 py-8 flex flex-col md:flex-row justify-between gap-4">
        <p>© 2026 VibeCircle. Find your people.</p>
        <p>Made for connections. Built around interests.</p>
      </footer>

    </main>
  );
}
