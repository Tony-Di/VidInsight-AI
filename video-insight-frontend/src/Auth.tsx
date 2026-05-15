import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type FormEvent,
} from 'react';
import { loginApi, registerApi, saveAuth, type UserProfile } from './api';

type Mode = 'signin' | 'signup';
type Lang = 'zh' | 'en';

interface AuthProps {
  onAuthenticated: (user: UserProfile) => void;
}

/* ──────────────────────────────────────────────────────────────
 * Auth (sign in + sign up) — single page, mode-toggled.
 * Bilingual (zh / en) — language is persisted under the same
 * `vi-lang` localStorage key used by the main app, so the choice
 * carries over after the user signs in.
 * ────────────────────────────────────────────────────────────── */

const LANG_STORAGE = 'vi-lang';

/* Scramble + particle pools are visual-only / language-neutral —
   they all look like code identifiers and are kept in English in
   both locales. The marquee items, in contrast, are descriptive
   sentences and get translated below. */

const SCRAMBLE_WORDS = [
  'transcript',
  'chapters',
  'entities',
  'scene graph',
  'summary',
  'structure',
];

// 漂浮粒子的文案。故意贴合后端真实做了的事:Spring Security/JWT、Redis Cache Aside、
// Redisson 分布式锁、RabbitMQ DLQ、yt-dlp、ffmpeg、SiliconFlow ASR + DeepSeek。
// 面试官 hover 一眼就能识别出"这是个 Spring Boot 真项目"。
const FRAGMENTS = [
  'cache.aside()',
  'SCAN list:user:42:*',
  'evictDetail(id=7)',
  'RLock acquired · 2.4s',
  'tryLock md5:a3f1...',
  'WatchDog renew',
  'verifyWith HS256',
  'BCrypt rounds=10',
  'ffmpeg -ar 16000',
  'yt-dlp merge mp4',
  'POST /v1/asr',
  'DeepSeek-V4-Flash',
  'rabbitmq ack',
  'DLQ routing',
  'JWT exp · 24h',
  'spring.security',
];

const CHARSET = 'abcdefghijklmnopqrstuvwxyz0123456789·░▒▓'.split('');

const TRANSLATIONS = {
  en: {
    lang_toggle: 'EN · 中',
    /* brand panel */
    brand_title_signin: 'Sign in to your ',
    brand_title_signup: 'Build your ',
    brand_title_word: 'workbench',
    decoding_label: 'decoding →',
    brand_intro_signin:
      'Pick up where you left off — your library, pipelines, and analysis runs are exactly where you parked them.',
    brand_intro_signup:
      'Spin up your own private workbench in seconds. Drop a video, get transcript, chapters, and a summary back.',
    stat_params_label: 'params · multimodal',
    stat_lang_label: 'languages · auto',
    stat_realtime_label: 'realtime · p50',
    /* marquee — keep these aligned with what the backend actually does */
    marquee_jwt: 'JWT auth · BCrypt',
    marquee_cache: 'Redis Cache Aside',
    marquee_lock: 'Redisson distributed lock',
    marquee_dedup: 'MD5 dedup per user',
    marquee_mq: 'RabbitMQ + DLQ',
    marquee_chunked: 'chunked upload · 5MB',
    marquee_ytdlp: 'yt-dlp · mp4',
    marquee_asr: 'SiliconFlow ASR',
    marquee_summary: 'DeepSeek summary',
    marquee_isolation: 'per-user data isolation',
    /* card */
    eyebrow_signin: 'sign in',
    eyebrow_signup: 'create account',
    heading_signin: 'Welcome back',
    heading_signup: 'Set up your workbench',
    sub_signin: 'Continue to your workbench with your email and password.',
    sub_signup: 'Just an email and password — no waiting list, no card.',
    placeholder_email: 'you@studio.tv',
    placeholder_password_signin: '••••••••',
    placeholder_password_signup: 'at least 8 characters',
    placeholder_confirm: 'confirm password',
    placeholder_display_name: 'display name (optional)',
    show: 'SHOW',
    hide: 'HIDE',
    forgot: 'Forgot password →',
    submit_signin: 'Sign in to VidInsight',
    submit_signup: 'Create your workbench',
    submitting_signin: 'Signing you in…',
    submitting_signup: 'Creating workbench…',
    switch_signin_prompt: 'New here? ',
    switch_signin_link: 'Create a free workbench →',
    switch_signup_prompt: 'Already have one? ',
    switch_signup_link: 'Sign in →',
    /* errors + footer */
    err_password_short: 'Password must be at least 8 characters.',
    err_password_mismatch: "Passwords don't match.",
    err_fallback: 'authentication failed',
    operational: 'all systems operational',
    footer_copy: '© 2026 VidInsight',
  },
  zh: {
    lang_toggle: 'EN · 中',
    brand_title_signin: '登录到你的',
    brand_title_signup: '搭建你的',
    brand_title_word: '工作台',
    decoding_label: '解码 →',
    brand_intro_signin:
      '从你离开的地方继续 — 视频库、流水线、分析记录都在原处等你。',
    brand_intro_signup:
      '几秒钟内搭好你的私人工作台。上传视频,自动拿到字幕、章节和总结。',
    stat_params_label: '参数 · 多模态',
    stat_lang_label: '语言 · 自动',
    stat_realtime_label: '实时倍率 · p50',
    marquee_jwt: 'JWT 认证 · BCrypt',
    marquee_cache: 'Redis Cache Aside 缓存',
    marquee_lock: 'Redisson 分布式锁',
    marquee_dedup: '按用户 MD5 去重',
    marquee_mq: 'RabbitMQ + 死信队列',
    marquee_chunked: '分片上传 · 5MB',
    marquee_ytdlp: 'yt-dlp · mp4',
    marquee_asr: 'SiliconFlow ASR',
    marquee_summary: 'DeepSeek 摘要',
    marquee_isolation: '多用户数据隔离',
    eyebrow_signin: '登录',
    eyebrow_signup: '注册',
    heading_signin: '欢迎回来',
    heading_signup: '搭建你的工作台',
    sub_signin: '使用邮箱和密码继续到你的工作台。',
    sub_signup: '只需邮箱和密码 — 无候补名单,无需信用卡。',
    placeholder_email: 'you@studio.tv',
    placeholder_password_signin: '••••••••',
    placeholder_password_signup: '至少 8 个字符',
    placeholder_confirm: '确认密码',
    placeholder_display_name: '显示名(选填)',
    show: '显示',
    hide: '隐藏',
    forgot: '忘记密码 →',
    submit_signin: '登录 VidInsight',
    submit_signup: '创建工作台',
    submitting_signin: '登录中…',
    submitting_signup: '创建中…',
    switch_signin_prompt: '还没账号?',
    switch_signin_link: '免费创建工作台 →',
    switch_signup_prompt: '已有账号?',
    switch_signup_link: '登录 →',
    err_password_short: '密码至少需要 8 个字符。',
    err_password_mismatch: '两次输入的密码不一致。',
    err_fallback: '身份验证失败',
    operational: '服务一切正常',
    footer_copy: '© 2026 VidInsight',
  },
} as const;

type I18nKey = keyof (typeof TRANSLATIONS)['en'];

const MARQUEE_KEYS: I18nKey[] = [
  'marquee_jwt',
  'marquee_cache',
  'marquee_lock',
  'marquee_dedup',
  'marquee_mq',
  'marquee_chunked',
  'marquee_ytdlp',
  'marquee_asr',
  'marquee_summary',
  'marquee_isolation',
];

/* ────────── icons ────────── */
function MailIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.25"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="2" y="3.5" width="12" height="9" rx="1.5" />
      <path d="M2 4l6 4 6-4" />
    </svg>
  );
}
function LockIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.25"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="3" y="7" width="10" height="7" rx="1.5" />
      <path d="M5 7V5a3 3 0 1 1 6 0v2" />
    </svg>
  );
}
function UserIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.25"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="8" cy="5.5" r="2.5" />
      <path d="M3 13c0-2.5 2-4 5-4s5 1.5 5 4" />
    </svg>
  );
}
function ArrowIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3.5 8H12.5M12.5 8L9 4.5M12.5 8L9 11.5" />
    </svg>
  );
}

/* ────────── logo with draw-in ────────── */
function LogoDraw({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="vi-auth-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="oklch(0.82 0.13 70)" />
          <stop offset="100%" stopColor="oklch(0.60 0.13 50)" />
        </linearGradient>
      </defs>
      <path className="vi-auth-logo-fill" d="M3 4 L12 20 L12 12 Z" fill="url(#vi-auth-grad)" />
      <path
        className="vi-auth-logo-stroke"
        d="M21 4 L12 20 L12 12 Z"
        fill="none"
        stroke="oklch(0.78 0.13 70)"
        strokeWidth="1.5"
        strokeLinejoin="round"
        style={{ ['--len' as string]: '60' } as CSSProperties}
      />
    </svg>
  );
}

/* ────────── scramble hook ────────── */
function useScramble(words: string[], interval = 2400) {
  const [text, setText] = useState(words[0]);

  useEffect(() => {
    let raf = 0;
    let idx = 0;
    let timer = 0;
    const scrambleTo = (target: string) => {
      let frame = 0;
      const total = 14;
      const tick = () => {
        frame += 1;
        let out = '';
        for (let i = 0; i < target.length; i++) {
          const settled = i < Math.floor((frame / total) * target.length);
          out += settled
            ? target[i]
            : CHARSET[Math.floor(Math.random() * CHARSET.length)];
        }
        setText(out);
        if (frame < total) raf = requestAnimationFrame(tick);
        else setText(target);
      };
      tick();
    };
    timer = window.setInterval(() => {
      idx = (idx + 1) % words.length;
      scrambleTo(words[idx]);
    }, interval);
    return () => {
      window.clearInterval(timer);
      if (raf) cancelAnimationFrame(raf);
    };
  }, [words, interval]);

  return text;
}

/* ────────── aurora background ────────── */
function Aurora() {
  return (
    <div className="vi-auth-aurora" aria-hidden="true">
      <div className="vi-auth-blob a" />
      <div className="vi-auth-blob b" />
      <div className="vi-auth-blob c" />
      <div className="vi-auth-blob d" />
      <div className="vi-auth-grain" />
    </div>
  );
}

/* ────────── floating particles ────────── */
function Particles({ count = 14 }: { count?: number }) {
  const items = useMemo(
    () =>
      Array.from({ length: count }).map((_, i) => ({
        key: i,
        text: FRAGMENTS[i % FRAGMENTS.length],
        left: `${Math.round(Math.random() * 96)}%`,
        delay: `${(-Math.random() * 18).toFixed(2)}s`,
        duration: `${(14 + Math.random() * 12).toFixed(2)}s`,
        driftX: `${(Math.random() * 80 - 40).toFixed(0)}px`,
        fontSize: `${(10 + Math.random() * 3).toFixed(1)}px`,
        opacity: 0.4 + Math.random() * 0.5,
      })),
    [count],
  );
  return (
    <div className="vi-auth-particles" aria-hidden="true">
      {items.map((p) => (
        <span
          key={p.key}
          className="vi-auth-particle"
          style={
            {
              left: p.left,
              animationDelay: p.delay,
              animationDuration: p.duration,
              fontSize: p.fontSize,
              opacity: p.opacity,
              ['--drift-x' as string]: p.driftX,
            } as CSSProperties
          }
        >
          {p.text}
        </span>
      ))}
    </div>
  );
}

/* ────────── marquee strip ────────── */
function Marquee({ t }: { t: (k: I18nKey) => string }) {
  /* Duplicate the (translated) list so the loop is seamless. */
  const items = useMemo(
    () => [...MARQUEE_KEYS, ...MARQUEE_KEYS].map((k) => t(k)),
    [t],
  );
  return (
    <div className="vi-auth-marquee">
      <div className="vi-auth-marquee-mask l" />
      <div className="vi-auth-marquee-mask r" />
      <div className="vi-auth-marquee-track">
        {items.map((text, i) => (
          <span key={i} className="vi-auth-marquee-item">
            {text}
          </span>
        ))}
      </div>
    </div>
  );
}

/* ────────── left brand panel ────────── */
function BrandPanel({
  mode,
  lang,
  t,
}: {
  mode: Mode;
  lang: Lang;
  t: (k: I18nKey) => string;
}) {
  const word = useScramble(SCRAMBLE_WORDS, 2400);
  const isSignin = mode === 'signin';
  return (
    <section className="vi-auth-brand">
      <Aurora />
      <Particles count={16} />
      <div className="vi-auth-brand-content">
        <div
          className="anim-up stagger-1"
          style={{ display: 'flex', alignItems: 'center', gap: 12 }}
        >
          <LogoDraw size={32} />
          <span style={{ fontSize: 15, fontWeight: 600, letterSpacing: '-0.01em' }}>
            VidInsight{' '}
            <span style={{ color: 'var(--text-3)', fontWeight: 500 }}>AI</span>
          </span>
        </div>

        <div
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            gap: 22,
          }}
        >
          <h1
            className="anim-up stagger-3"
            style={{
              margin: 0,
              fontSize: 'clamp(32px, 4vw, 46px)',
              lineHeight: 1.06,
              letterSpacing: '-0.025em',
              fontWeight: 500,
              maxWidth: 520,
            }}
          >
            {t(isSignin ? 'brand_title_signin' : 'brand_title_signup')}
            {/* Instrument Serif italic only shapes Latin glyphs, so for
                Chinese the serif accent is dropped and we just rely on
                the accent colour to make the word pop. */}
            <span
              className={lang === 'en' ? 'serif' : ''}
              style={{
                color: 'var(--accent)',
                fontWeight: lang === 'en' ? 400 : 500,
              }}
            >
              {t('brand_title_word')}
            </span>
            <span style={{ color: 'var(--accent)' }}>
              {lang === 'zh' ? '。' : '.'}
            </span>
          </h1>

          <div className="anim-up stagger-4 vi-auth-scramble">
            <span className="vi-auth-scramble-label">{t('decoding_label')}</span>
            <span className="vi-auth-scramble-word">
              {word}
              <span style={{ opacity: 0.5, marginLeft: 2 }}>▮</span>
            </span>
          </div>

          <p
            className="anim-up stagger-4"
            style={{
              margin: 0,
              maxWidth: 460,
              fontSize: 14,
              color: 'var(--text-2)',
              lineHeight: 1.6,
            }}
          >
            {t(isSignin ? 'brand_intro_signin' : 'brand_intro_signup')}
          </p>

          <div className="anim-up stagger-5 vi-auth-stats">
            <div className="vi-auth-stat">
              <div className="vi-auth-stat-big num">
                8.4{' '}
                <span style={{ color: 'var(--text-3)', fontSize: 12, fontWeight: 400 }}>
                  B
                </span>
              </div>
              <div className="vi-auth-stat-sm">{t('stat_params_label')}</div>
            </div>
            <div className="vi-auth-stat">
              <div className="vi-auth-stat-big num">38</div>
              <div className="vi-auth-stat-sm">{t('stat_lang_label')}</div>
            </div>
            <div className="vi-auth-stat">
              <div className="vi-auth-stat-big num">0.31×</div>
              <div className="vi-auth-stat-sm">{t('stat_realtime_label')}</div>
            </div>
          </div>
        </div>

        <Marquee t={t} />
      </div>
    </section>
  );
}

/* ────────── main auth component ────────── */
export default function Auth({ onAuthenticated }: AuthProps) {
  const [mode, setMode] = useState<Mode>('signin');
  const [lang, setLang] = useState<Lang>(() => {
    const stored = localStorage.getItem(LANG_STORAGE);
    return stored === 'zh' || stored === 'en' ? stored : 'en';
  });
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [password2, setPassword2] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /* Memoised translator so child components don't re-render
     whenever something unrelated in this component updates. */
  const t = useCallback(
    (k: I18nKey): string => TRANSLATIONS[lang][k],
    [lang],
  );

  /* Persist language so the choice survives reloads + carries into
     the main app (AppInner reads the same `vi-lang` key). */
  useEffect(() => {
    localStorage.setItem(LANG_STORAGE, lang);
  }, [lang]);

  const cardRef = useRef<HTMLDivElement | null>(null);
  const cursorGlowRef = useRef<HTMLDivElement | null>(null);

  /* Global cursor spotlight — mirrors the home page. Writes the
     pointer position (in viewport px and normalised −1..1) onto
     :root so the fixed-positioned .cursor-glow blob can follow,
     and arms it the first time the user actually moves the mouse. */
  useEffect(() => {
    let frame = 0;
    let mx = 0;
    let my = 0;
    let px = 0;
    let py = 0;
    const onMove = (e: PointerEvent) => {
      mx = e.clientX;
      my = e.clientY;
      px = (e.clientX / window.innerWidth - 0.5) * 2;
      py = (e.clientY / window.innerHeight - 0.5) * 2;
      if (frame) return;
      frame = requestAnimationFrame(() => {
        const root = document.documentElement;
        root.style.setProperty('--mx', `${mx}px`);
        root.style.setProperty('--my', `${my}px`);
        root.style.setProperty('--px', px.toFixed(3));
        root.style.setProperty('--py', py.toFixed(3));
        cursorGlowRef.current?.classList.add('is-armed');
        frame = 0;
      });
    };
    window.addEventListener('pointermove', onMove, { passive: true });
    return () => {
      window.removeEventListener('pointermove', onMove);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  /* 3D tilt + card-local glow tracker. Both effects need the same
     pointer-vs-card rect calculation, so they share a single
     pointermove listener on the perspective wrapper. The tilt
     writes --rx / --ry; the glow writes --cmx / --cmy in the
     card's local space (px from its top-left). */
  useEffect(() => {
    const el = cardRef.current;
    if (!el) return;
    const wrap = el.parentElement;
    if (!wrap) return;
    let frame = 0;
    const onMove = (e: PointerEvent) => {
      const r = el.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      const dx = (e.clientX - cx) / (r.width / 2);
      const dy = (e.clientY - cy) / (r.height / 2);
      const cmx = e.clientX - r.left;
      const cmy = e.clientY - r.top;
      if (frame) return;
      frame = requestAnimationFrame(() => {
        el.style.setProperty('--rx', `${(dx * 5).toFixed(2)}deg`);
        el.style.setProperty('--ry', `${(-dy * 4).toFixed(2)}deg`);
        el.style.setProperty('--cmx', `${cmx.toFixed(1)}px`);
        el.style.setProperty('--cmy', `${cmy.toFixed(1)}px`);
        frame = 0;
      });
    };
    const onLeave = () => {
      el.style.setProperty('--rx', '0deg');
      el.style.setProperty('--ry', '0deg');
    };
    wrap.addEventListener('pointermove', onMove);
    wrap.addEventListener('pointerleave', onLeave);
    return () => {
      wrap.removeEventListener('pointermove', onMove);
      wrap.removeEventListener('pointerleave', onLeave);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  function switchMode() {
    setError(null);
    setPassword('');
    setPassword2('');
    setShowPw(false);
    setMode((m) => (m === 'signin' ? 'signup' : 'signin'));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (submitting) return;
    setError(null);

    if (mode === 'signup') {
      if (password.length < 8) {
        setError(t('err_password_short'));
        return;
      }
      if (password !== password2) {
        setError(t('err_password_mismatch'));
        return;
      }
    }

    setSubmitting(true);
    try {
      const res =
        mode === 'signin'
          ? await loginApi(email.trim(), password)
          : await registerApi(
              email.trim(),
              password,
              displayName.trim() || undefined,
            );
      saveAuth(res);
      onAuthenticated(res.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('err_fallback'));
    } finally {
      setSubmitting(false);
    }
  }

  const isSignin = mode === 'signin';

  return (
    <main className="vi-auth-stage">
      <div ref={cursorGlowRef} className="cursor-glow" aria-hidden="true" />
      <BrandPanel mode={mode} lang={lang} t={t} />

      <button
        type="button"
        className="vi-auth-lang"
        title={lang === 'zh' ? 'Switch to English' : '切换到中文'}
        aria-label={lang === 'zh' ? 'Switch to English' : '切换到中文'}
        onClick={() => setLang((l) => (l === 'zh' ? 'en' : 'zh'))}
      >
        <span className={lang === 'en' ? 'is-active' : ''}>EN</span>
        <span style={{ color: 'var(--text-4)' }}>·</span>
        <span className={lang === 'zh' ? 'is-active' : ''}>中</span>
      </button>

      <section className="vi-auth-form-panel">
        <div className="vi-auth-tilt-wrap">
          <div
            ref={cardRef}
            className="vi-auth-tilt vi-auth-card glow-host anim-in stagger-2"
          >
            <div className="glow-spot" />
            <div className="glow-border" />
            <div className="anim-up stagger-3" style={{ marginBottom: 22 }}>
              <div
                className="mono"
                style={{
                  fontSize: 11,
                  color: 'var(--text-3)',
                  letterSpacing: '0.08em',
                  textTransform: 'uppercase',
                }}
              >
                {t(isSignin ? 'eyebrow_signin' : 'eyebrow_signup')}
              </div>
              <h2
                style={{
                  margin: '6px 0 0',
                  fontSize: 22,
                  lineHeight: 1.2,
                  letterSpacing: '-0.02em',
                  fontWeight: 500,
                }}
              >
                {t(isSignin ? 'heading_signin' : 'heading_signup')}
                <span style={{ color: 'var(--accent)' }}>.</span>
              </h2>
              <p style={{ margin: '8px 0 0', fontSize: 13, color: 'var(--text-2)' }}>
                {t(isSignin ? 'sub_signin' : 'sub_signup')}
              </p>
            </div>

            <form
              onSubmit={handleSubmit}
              className="vi-auth-field-stack anim-up stagger-4"
              noValidate
            >
              <label className="vi-auth-field">
                <span className="vi-auth-field-icon">
                  <MailIcon />
                </span>
                <input
                  type="email"
                  required
                  autoComplete="email"
                  placeholder={t('placeholder_email')}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </label>

              <label className="vi-auth-field">
                <span className="vi-auth-field-icon">
                  <LockIcon />
                </span>
                <input
                  type={showPw ? 'text' : 'password'}
                  required
                  minLength={isSignin ? undefined : 8}
                  autoComplete={isSignin ? 'current-password' : 'new-password'}
                  placeholder={t(
                    isSignin ? 'placeholder_password_signin' : 'placeholder_password_signup',
                  )}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <button
                  type="button"
                  className="vi-auth-field-action"
                  tabIndex={-1}
                  onClick={(e) => {
                    e.preventDefault();
                    setShowPw((s) => !s);
                  }}
                >
                  {showPw ? t('hide') : t('show')}
                </button>
              </label>

              {!isSignin && (
                <>
                  <label className="vi-auth-field">
                    <span className="vi-auth-field-icon">
                      <LockIcon />
                    </span>
                    <input
                      type={showPw ? 'text' : 'password'}
                      required
                      autoComplete="new-password"
                      placeholder={t('placeholder_confirm')}
                      value={password2}
                      onChange={(e) => setPassword2(e.target.value)}
                    />
                  </label>
                  <label className="vi-auth-field">
                    <span className="vi-auth-field-icon">
                      <UserIcon />
                    </span>
                    <input
                      type="text"
                      autoComplete="nickname"
                      placeholder={t('placeholder_display_name')}
                      maxLength={80}
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                    />
                  </label>
                </>
              )}

              {isSignin && (
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'flex-end',
                    alignItems: 'center',
                    paddingTop: 4,
                  }}
                >
                  <a
                    href="#"
                    className="mono"
                    style={{ fontSize: 11.5, color: 'var(--text-3)' }}
                    onClick={(e) => e.preventDefault()}
                  >
                    {t('forgot')}
                  </a>
                </div>
              )}

              {error && (
                <div className="vi-auth-error" role="alert">
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={submitting}
                className="vi-auth-btn vi-auth-btn-primary"
                style={{ marginTop: 6 }}
              >
                {submitting ? (
                  <>
                    <span className="vi-auth-spinner" />
                    {t(isSignin ? 'submitting_signin' : 'submitting_signup')}
                  </>
                ) : (
                  <>
                    {t(isSignin ? 'submit_signin' : 'submit_signup')}
                    <ArrowIcon />
                  </>
                )}
              </button>
            </form>

            <div className="vi-auth-switch anim-up stagger-6">
              {isSignin ? (
                <>
                  {t('switch_signin_prompt')}
                  <button
                    type="button"
                    className="vi-auth-switch-link"
                    onClick={switchMode}
                  >
                    {t('switch_signin_link')}
                  </button>
                </>
              ) : (
                <>
                  {t('switch_signup_prompt')}
                  <button
                    type="button"
                    className="vi-auth-switch-link"
                    onClick={switchMode}
                  >
                    {t('switch_signup_link')}
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      </section>

      <div className="vi-auth-footer">
        <span>{t('footer_copy')}</span>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <span
            style={{
              width: 5,
              height: 5,
              borderRadius: 99,
              background: 'var(--ok)',
            }}
          />
          {t('operational')}
        </span>
      </div>
    </main>
  );
}
