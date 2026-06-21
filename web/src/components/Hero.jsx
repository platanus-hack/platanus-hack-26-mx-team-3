import { useEffect, useRef } from "react";

const DOWNLOAD_APK_URL = "#";

export default function Hero() {
  const videoRef = useRef(null);

  // Respeta prefers-reduced-motion: no autoreproducir el video de fondo.
  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce && videoRef.current) {
      videoRef.current.removeAttribute("autoplay");
      videoRef.current.pause();
    }
  }, []);

  return (
    <section
      id="top"
      className="relative grid min-h-[100svh] place-items-center overflow-hidden text-center"
      aria-labelledby="hero-title"
    >
      {/* Video de fondo */}
      <video
        ref={videoRef}
        className="absolute inset-0 h-full w-full object-cover"
        autoPlay
        muted
        loop
        playsInline
        preload="auto"
        aria-hidden="true"
      >
        <source src="/resources/gatitos.mp4" type="video/mp4" />
      </video>

      {/* Capa oscura para legibilidad */}
      <div
        className="absolute inset-0"
        aria-hidden="true"
        style={{
          background: "rgba(0,0,0,.76)",
        }}
      />

      <div
        className="pointer-events-none absolute left-[-7rem] top-[18%] h-[34rem] w-[22rem] rounded-full opacity-35 blur-3xl sm:left-[-2rem] md:left-[4%]"
        aria-hidden="true"
        style={{
          background:
            "linear-gradient(135deg, rgba(107,127,232,.55) 0%, rgba(168,154,232,.42) 100%)",
          mixBlendMode: "screen",
        }}
      />

      {/* Contenido */}
      <div className="relative z-10 flex min-w-0 w-full justify-self-stretch flex-col items-center gap-10 px-6">
        <h1
          id="hero-title"
          className="animate-hero-in w-full max-w-[11ch] font-title text-[2.625rem] font-extrabold leading-[1.05] text-snow sm:max-w-[16ch] sm:text-6xl md:text-7xl"
          style={{ textShadow: "0 4px 30px rgba(0,0,0,.45)" }}
        >
          Haz <span className="highlight-gradient-text">visible</span> la conversación
        </h1>

        {/* CTAs debajo del texto */}
        <div className="animate-cue flex w-full max-w-[320px] flex-col items-center gap-3 sm:max-w-none sm:flex-row sm:justify-center sm:gap-4">
          <a
            href={DOWNLOAD_APK_URL}
            className="inline-flex w-full items-center justify-center gap-2 rounded-full border border-[#2A2A2A] bg-night px-7 py-3.5 font-title text-base font-semibold text-snow shadow-[0_12px_34px_rgba(0,0,0,.34)] transition-transform duration-300 hover:-translate-y-0.5 active:translate-y-0 active:scale-[0.98] sm:w-auto"
          >
            Instalar APK
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
              className="h-5 w-5 shrink-0 fill-none stroke-current stroke-[2.4]"
              style={{ strokeLinecap: "round", strokeLinejoin: "round" }}
            >
              <path d="M12 3v12" />
              <path d="m7 10 5 5 5-5" />
              <path d="M5 21h14" />
            </svg>
          </a>

          <a
            href="#about"
            className="inline-flex w-full items-center justify-center gap-2 rounded-full bg-panel-soft px-7 py-3.5 font-title text-base font-semibold text-snow shadow-[0_12px_34px_rgba(0,0,0,.28)] transition-transform duration-300 hover:-translate-y-0.5 active:translate-y-0 active:scale-[0.98] sm:w-auto"
          >
            Conócenos
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
              className="h-5 w-5 shrink-0 fill-none stroke-current stroke-[2.4]"
              style={{ strokeLinecap: "round", strokeLinejoin: "round" }}
            >
              <path d="M6 9l6 6 6-6" />
            </svg>
          </a>
        </div>
      </div>
    </section>
  );
}
