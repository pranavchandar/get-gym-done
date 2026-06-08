// ui.jsx — Shared primitive components: icons, placeholders, charts, lists.

// ---------- Icons (simple inline SVG, single stroke) ----------
const Icon = {
  back: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M19 12H5M12 19l-7-7 7-7"/></svg>),
  close: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M18 6L6 18M6 6l12 12"/></svg>),
  plus: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12 5v14M5 12h14"/></svg>),
  minus: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M5 12h14"/></svg>),
  check: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M20 6L9 17l-5-5"/></svg>),
  chev: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M9 18l6-6-6-6"/></svg>),
  chevDown: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M6 9l6 6 6-6"/></svg>),
  home: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M3 11l9-8 9 8v10a2 2 0 01-2 2H5a2 2 0 01-2-2V11z"/></svg>),
  dumbbell: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M6.5 6.5l11 11M3 9l3-3M21 15l-3 3M5 11l-2 2 4 4 2-2M19 13l2-2-4-4-2 2"/></svg>),
  chart: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M3 3v18h18M7 14l4-4 4 3 5-6"/></svg>),
  user: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="8" r="4"/><path d="M4 21v-1a7 7 0 0114 0v1"/></svg>),
  cog: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 00.34 1.87l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.7 1.7 0 00-1.87-.34 1.7 1.7 0 00-1.04 1.56V21a2 2 0 11-4 0v-.09a1.7 1.7 0 00-1.1-1.55 1.7 1.7 0 00-1.87.34l-.06.06a2 2 0 11-2.83-2.83l.06-.06a1.7 1.7 0 00.34-1.87 1.7 1.7 0 00-1.56-1.04H3a2 2 0 110-4h.09A1.7 1.7 0 004.6 9a1.7 1.7 0 00-.34-1.87l-.06-.06a2 2 0 112.83-2.83l.06.06a1.7 1.7 0 001.87.34h.04a1.7 1.7 0 001.04-1.56V3a2 2 0 114 0v.09a1.7 1.7 0 001.04 1.56 1.7 1.7 0 001.87-.34l.06-.06a2 2 0 112.83 2.83l-.06.06a1.7 1.7 0 00-.34 1.87v.04a1.7 1.7 0 001.56 1.04H21a2 2 0 110 4h-.09a1.7 1.7 0 00-1.56 1.04z"/></svg>),
  fire: (p) => (<svg viewBox="0 0 24 24" fill="currentColor" {...p}><path d="M13 2s4 3 4 8a4 4 0 01-4 4 4 4 0 01-3-1.5C9 16 8 17 8 19c0 2 2 3 4 3 4 0 8-3 8-8 0-5-3-9-7-12zM10 22c-2 0-3-2-3-3 0-1 .3-2 1-3-1 0-2 1-2 3 0 2 1 3 4 3z"/></svg>),
  flame: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12 2s5 5 5 10a5 5 0 11-10 0c0-3 2-5 2-8 1 1 2 3 3 4 0-2 0-4 0-6z"/></svg>),
  timer: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><circle cx="12" cy="13" r="8"/><path d="M12 9v4l2 2M9 2h6"/></svg>),
  edit: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M12 20h9M16.5 3.5a2.12 2.12 0 113 3L7 19l-4 1 1-4 12.5-12.5z"/></svg>),
  more: (p) => (<svg viewBox="0 0 24 24" fill="currentColor" {...p}><circle cx="12" cy="5" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="12" cy="19" r="1.6"/></svg>),
  swap: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M7 7h11l-3-3M17 17H6l3 3"/></svg>),
  play: (p) => (<svg viewBox="0 0 24 24" fill="currentColor" {...p}><path d="M6 4l14 8-14 8z"/></svg>),
  pause: (p) => (<svg viewBox="0 0 24 24" fill="currentColor" {...p}><rect x="6" y="5" width="4" height="14"/><rect x="14" y="5" width="4" height="14"/></svg>),
  filter: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M4 6h16M7 12h10M10 18h4"/></svg>),
  arrow: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M5 12h14M12 5l7 7-7 7"/></svg>),
  trend: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M3 17l6-6 4 4 8-9"/><path d="M14 6h7v7"/></svg>),
  bell: (p) => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}><path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 01-3.4 0"/></svg>),
};

// ---------- Striped placeholder for "exercise GIF" / "muscle map" ----------
function StripedPlaceholder({ label = 'exercise gif', height = 200, style }) {
  return (
    <div className="striped" style={{ height, borderRadius: 14, ...style }}>
      <div className="striped-label">{label}</div>
    </div>
  );
}

// ---------- Phone status bar (custom) ----------
function PhoneStatusBar({ time = '9:30', dark = true }) {
  const color = 'var(--fg)';
  return (
    <div className="statusbar">
      <span style={{ width: 60 }}>{time}</span>
      <div className="punch" />
      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <svg width="14" height="11" viewBox="0 0 14 11"><path d="M0 9.5L1 8c2.5-2.5 6.5-2.5 9 0l1 1.5L7 13zM3 6l1-1c1.7-1.7 4.3-1.7 6 0l1 1L7 10z" fill={color}/></svg>
        <svg width="16" height="11" viewBox="0 0 16 11"><path d="M0 11h2V8H0v3zm4 0h2V5H4v6zm4 0h2V2H8v9zm4 0h2V0h-2v11z" fill={color}/></svg>
        <svg width="22" height="11" viewBox="0 0 22 11"><rect x="0.5" y="1" width="18" height="9" rx="2" stroke={color} fill="none" opacity="0.5"/><rect x="2" y="2.5" width="13" height="6" rx="0.5" fill={color}/><rect x="19.5" y="3.5" width="1.5" height="4" rx="0.5" fill={color}/></svg>
      </div>
    </div>
  );
}

// ---------- Phone gesture nav ----------
function GestureNav() {
  return (
    <div className="gesture-nav">
      <div className="gesture-nav-bar" />
    </div>
  );
}

// ---------- Bottom tab bar (4 tabs) ----------
function TabBar({ current, onChange }) {
  const tabs = [
    { id: 'home', label: 'Today', icon: 'home' },
    { id: 'workouts', label: 'Workouts', icon: 'dumbbell' },
    { id: 'profile', label: 'Profile', icon: 'chart' },
    { id: 'settings', label: 'Settings', icon: 'cog' },
  ];
  return (
    <div className="tabbar">
      {tabs.map(t => {
        const IconComp = Icon[t.icon];
        return (
          <div key={t.id} className={`tab ${current === t.id ? 'active' : ''}`} onClick={() => onChange(t.id)}>
            <IconComp />
            <span>{t.label}</span>
          </div>
        );
      })}
    </div>
  );
}

// ---------- Top app bar (custom) ----------
function TopBar({ title, sub, left, right, big = false }) {
  return (
    <div style={{ padding: big ? '20px 22px 12px' : '14px 18px 12px', display: 'flex', alignItems: 'flex-start', gap: 10 }}>
      <div style={{ width: 36, height: 36, flexShrink: 0 }}>
        {left || null}
      </div>
      <div style={{ flex: 1, paddingTop: 4 }}>
        {sub && <div className="eyebrow" style={{ marginBottom: 4 }}>{sub}</div>}
        <div className="display" style={{ fontSize: big ? 36 : 22, color: 'var(--fg)' }}>{title}</div>
      </div>
      <div style={{ width: 36, height: 36, flexShrink: 0, display: 'flex', justifyContent: 'flex-end' }}>
        {right || null}
      </div>
    </div>
  );
}

function IconBtn({ icon, onClick, style }) {
  const IconComp = Icon[icon];
  return (
    <button
      onClick={onClick}
      style={{
        width: 36, height: 36, borderRadius: 10,
        background: 'var(--surface-2)', color: 'var(--fg)',
        border: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center',
        cursor: 'pointer', padding: 0,
        ...style,
      }}>
      <IconComp width={20} height={20} />
    </button>
  );
}

// ---------- Sparkline / Line chart ----------
function LineChart({ data, height = 140, padding = 12, showAxis = true, color = 'var(--accent)' }) {
  if (!data || data.length === 0) return <div style={{ height }} className="striped"><div className="striped-label">no data</div></div>;
  const xs = data.map((_, i) => i);
  const ys = data.map(d => d.y);
  const min = Math.min(...ys);
  const max = Math.max(...ys);
  const range = max - min || 1;
  const w = 320;
  const h = height;
  const px = padding, py = padding + 8;

  const point = (i, y) => {
    const x = px + (i / (xs.length - 1 || 1)) * (w - px * 2);
    const yy = py + (1 - (y - min) / range) * (h - py * 2);
    return [x, yy];
  };
  const pts = data.map((d, i) => point(i, d.y));
  const line = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
  const fill = line + ` L${pts[pts.length - 1][0]} ${h - py} L${pts[0][0]} ${h - py} Z`;

  return (
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" style={{ width: '100%', height, display: 'block' }} className="spark">
      <path className="fill" d={fill} fill={color} opacity={0.14} />
      <path className="line" d={line} stroke={color} strokeWidth={2.4} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      {pts.map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r={i === pts.length - 1 ? 4 : 2.4} fill={color} stroke="var(--bg)" strokeWidth={i === pts.length - 1 ? 2 : 0} />
      ))}
      {showAxis && (
        <>
          <text x={px} y={14} fill="var(--fg-3)" fontSize="10" fontFamily="JetBrains Mono">{max}</text>
          <text x={px} y={h - 2} fill="var(--fg-3)" fontSize="10" fontFamily="JetBrains Mono">{min}</text>
        </>
      )}
    </svg>
  );
}

// ---------- Tiny sparkline for cards ----------
function Sparkline({ values, color = 'var(--accent)', height = 32, width = 100 }) {
  if (!values || values.length < 2) return null;
  const min = Math.min(...values), max = Math.max(...values);
  const range = max - min || 1;
  const pts = values.map((v, i) => {
    const x = (i / (values.length - 1)) * width;
    const y = (1 - (v - min) / range) * (height - 4) + 2;
    return [x, y];
  });
  const line = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
      <path d={line} stroke={color} strokeWidth={1.6} fill="none" strokeLinecap="round" />
    </svg>
  );
}

// ---------- Body / muscle map (front view, abstract) ----------
// A simple symmetric body outline with muscle groups highlightable by id.
// Honest representation - silhouette only, not anatomical art.
function MuscleMap({ active = [], side = 'front', width = 120 }) {
  // muscle id -> SVG paths (flat blob shapes). We keep these simple and symmetric.
  const A = new Set(active.map(s => s.toLowerCase()));
  const isOn = (...names) => names.some(n => A.has(n));
  const C = (on) => on ? 'var(--accent)' : 'var(--surface-3)';
  const stroke = 'var(--line)';

  if (side === 'back') {
    return (
      <svg viewBox="0 0 100 200" width={width} style={{ display: 'block' }}>
        {/* head */}
        <circle cx="50" cy="14" r="9" fill="var(--surface-3)" stroke={stroke} />
        {/* torso outline base */}
        <path d="M30 30 Q50 26 70 30 L72 80 Q72 100 68 120 L62 150 Q58 165 56 180 L44 180 Q42 165 38 150 L32 120 Q28 100 28 80 Z" fill="var(--surface-3)" stroke={stroke} />
        {/* arms */}
        <path d="M28 32 Q18 60 18 90 L26 90 Q26 60 32 36 Z" fill={C(isOn('triceps','rear delts'))} stroke={stroke} />
        <path d="M72 32 Q82 60 82 90 L74 90 Q74 60 68 36 Z" fill={C(isOn('triceps','rear delts'))} stroke={stroke} />
        {/* lats (upper back) */}
        <path d="M32 36 Q40 50 38 80 L62 80 Q60 50 68 36 Q60 32 50 32 Q40 32 32 36 Z" fill={C(isOn('lats','mid back','rear delts','back'))} stroke={stroke} />
        {/* lower back / glutes */}
        <path d="M34 105 Q50 100 66 105 L66 130 Q50 134 34 130 Z" fill={C(isOn('glutes','posterior chain','lats'))} stroke={stroke} />
        {/* hamstrings */}
        <path d="M36 138 Q42 160 42 178 L48 178 Q48 160 44 138 Z" fill={C(isOn('hamstrings','hams','posterior chain'))} stroke={stroke} />
        <path d="M64 138 Q58 160 58 178 L52 178 Q52 160 56 138 Z" fill={C(isOn('hamstrings','hams','posterior chain'))} stroke={stroke} />
        {/* calves */}
        <path d="M40 180 Q40 192 44 196 L48 196 Q46 192 46 180 Z" fill={C(isOn('calves'))} stroke={stroke} />
        <path d="M60 180 Q60 192 56 196 L52 196 Q54 192 54 180 Z" fill={C(isOn('calves'))} stroke={stroke} />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 100 200" width={width} style={{ display: 'block' }}>
      {/* head */}
      <circle cx="50" cy="14" r="9" fill="var(--surface-3)" stroke={stroke} />
      {/* base torso */}
      <path d="M30 30 Q50 26 70 30 L72 80 Q72 100 68 120 L62 150 Q58 165 56 180 L44 180 Q42 165 38 150 L32 120 Q28 100 28 80 Z" fill="var(--surface-3)" stroke={stroke} />
      {/* shoulders / front delts */}
      <ellipse cx="32" cy="38" rx="9" ry="7" fill={C(isOn('shoulders','front delts','side delts'))} stroke={stroke} />
      <ellipse cx="68" cy="38" rx="9" ry="7" fill={C(isOn('shoulders','front delts','side delts'))} stroke={stroke} />
      {/* upper chest */}
      <path d="M34 36 Q50 36 66 36 L64 56 Q50 54 36 56 Z" fill={C(isOn('upper chest','chest'))} stroke={stroke} />
      {/* lower chest */}
      <path d="M36 56 Q50 60 64 56 L62 70 Q50 72 38 70 Z" fill={C(isOn('chest'))} stroke={stroke} />
      {/* biceps */}
      <path d="M22 42 Q14 60 18 80 L28 78 Q28 58 30 44 Z" fill={C(isOn('biceps'))} stroke={stroke} />
      <path d="M78 42 Q86 60 82 80 L72 78 Q72 58 70 44 Z" fill={C(isOn('biceps'))} stroke={stroke} />
      {/* forearms */}
      <path d="M18 80 Q14 100 14 116 L22 116 Q24 100 28 80 Z" fill={C(isOn('forearms'))} stroke={stroke} />
      <path d="M82 80 Q86 100 86 116 L78 116 Q76 100 72 80 Z" fill={C(isOn('forearms'))} stroke={stroke} />
      {/* abs */}
      <path d="M40 72 Q50 70 60 72 L60 110 Q50 112 40 110 Z" fill={C(isOn('abs','core'))} stroke={stroke} />
      <line x1="50" y1="72" x2="50" y2="110" stroke={stroke} strokeWidth="0.6" />
      <line x1="40" y1="85" x2="60" y2="85" stroke={stroke} strokeWidth="0.6" />
      <line x1="40" y1="97" x2="60" y2="97" stroke={stroke} strokeWidth="0.6" />
      {/* quads */}
      <path d="M34 120 Q42 150 42 175 L48 175 Q48 150 46 120 Z" fill={C(isOn('quads'))} stroke={stroke} />
      <path d="M66 120 Q58 150 58 175 L52 175 Q52 150 54 120 Z" fill={C(isOn('quads'))} stroke={stroke} />
      {/* adductors strip */}
      <path d="M46 120 L54 120 L52 160 L48 160 Z" fill={C(isOn('adductors'))} stroke={stroke} />
      {/* calves (front) */}
      <path d="M40 180 Q40 192 44 196 L48 196 Q46 192 46 180 Z" fill={C(isOn('calves'))} stroke={stroke} />
      <path d="M60 180 Q60 192 56 196 L52 196 Q54 192 54 180 Z" fill={C(isOn('calves'))} stroke={stroke} />
    </svg>
  );
}

// ---------- Helpers ----------
// Local-time date helpers (avoid UTC off-by-one on the calendar/heatmap).
function toLocalISO(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
function parseLocalISO(s) {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}
// Consistent streak = number of trailing weeks (back from this week) with >=3 sessions.
function computeWeekStreak(log, today) {
  let count = 0;
  for (let w = 0; w < 12; w++) {
    const weekStart = new Date(today); weekStart.setDate(weekStart.getDate() - (w + 1) * 7);
    const weekEnd = new Date(today); weekEnd.setDate(weekEnd.getDate() - w * 7);
    const cnt = log.filter(l => {
      const dt = parseLocalISO(l.date);
      return dt >= weekStart && dt < weekEnd;
    }).length;
    if (cnt >= 3) count++; else break;
  }
  return count;
}

function formatDate(iso) {
  const d = parseLocalISO(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function formatWeight(kg, unit = 'kg') {
  if (unit === 'lbs') return Math.round(kg * 2.20462 * 2) / 2;
  return kg;
}
function unitLabel(unit) { return unit === 'lbs' ? 'lbs' : 'kg'; }

// Export
Object.assign(window, {
  Icon, StripedPlaceholder, PhoneStatusBar, GestureNav,
  TabBar, TopBar, IconBtn, LineChart, Sparkline, MuscleMap,
  formatDate, formatWeight, unitLabel, toLocalISO, parseLocalISO, computeWeekStreak,
});
