// home.jsx — Today/Home screen with calendar + today card + warmup view

function HomeScreen({ workoutLog, nextDayIndex, onStartWorkout, onOpenDay, unit }) {
  // Build a 5-week calendar grid centered on today.
  const today = parseLocalISO(TODAY_ISO);
  const startOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
  const startDay = startOfMonth.getDay(); // 0 = sun
  const daysInMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate();
  const monthName = today.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

  // Build a map of date -> day index (1..5) from log.
  const logByDate = {};
  workoutLog.forEach(l => { logByDate[l.date] = l.day; });

  const cells = [];
  for (let i = 0; i < startDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) {
    const iso = toLocalISO(new Date(today.getFullYear(), today.getMonth(), d));
    cells.push({ d, iso });
  }

  const nextDay = WORKOUT_DAYS[nextDayIndex];

  // Recent activity stats
  const last7 = workoutLog.filter(l => {
    const diff = (today - parseLocalISO(l.date)) / (1000 * 60 * 60 * 24);
    return diff <= 7 && diff >= 0;
  });
  const streak = computeWeekStreak(workoutLog, today);

  return (
    <div className="scrollable" style={{ padding: '0 0 20px' }}>
      {/* Header */}
      <div style={{ padding: '6px 22px 16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="eyebrow">Today · {today.toLocaleDateString('en-US', { weekday: 'long' })}</div>
            <div className="display" style={{ fontSize: 38, marginTop: 6, lineHeight: 0.9 }}>
              Ready to<br/>get it<span style={{ color: 'var(--accent)' }}>.</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <IconBtn icon="bell" />
          </div>
        </div>
      </div>

      {/* Stat strip */}
      <div style={{ padding: '0 22px 16px', display: 'flex', gap: 8 }}>
        <StatPill label="Streak" value={streak + 'w'} icon="flame" />
        <StatPill label="This week" value={last7.length + '/5'} icon="check" />
        <StatPill label="Bodyweight" value={BODYWEIGHT[BODYWEIGHT.length - 1].kg.toFixed(1) + ' ' + unitLabel(unit)} icon="trend" />
      </div>

      {/* Today CTA card */}
      <div style={{ padding: '0 22px 18px' }}>
        <div style={{
          background: 'var(--accent)',
          color: 'var(--accent-fg)',
          borderRadius: 22,
          padding: 22,
          position: 'relative',
          overflow: 'hidden',
        }}>
          <div style={{
            position: 'absolute', right: -30, top: -30,
            fontFamily: 'Anton', fontSize: 200, opacity: 0.12,
            lineHeight: 0.8,
          }}>D{nextDayIndex + 1}</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, fontWeight: 700, letterSpacing: '0.12em', textTransform: 'uppercase' }}>
            <div className="pulse" style={{ width: 8, height: 8, borderRadius: 100, background: 'var(--accent-fg)' }} /> Up next
          </div>
          <div className="display" style={{ fontSize: 44, marginTop: 12, lineHeight: 0.85 }}>
            Day {nextDayIndex + 1}<br/>{nextDay.name}
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 14, fontSize: 12, fontWeight: 600 }}>
            <span>{nextDay.exercises.length} exercises</span>
            <span>·</span>
            <span>~{nextDay.exercises.length * 11} min</span>
            <span>·</span>
            <span>{nextDay.exercises.reduce((s, e) => s + e.sets, 0)} sets</span>
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 18 }}>
            <button
              onClick={() => onStartWorkout(nextDayIndex)}
              style={{
                flex: 1, background: 'var(--accent-fg)', color: 'var(--accent)',
                border: 'none', padding: '16px 18px', borderRadius: 12,
                fontFamily: 'Anton', fontSize: 22, letterSpacing: '0.04em',
                textTransform: 'uppercase', cursor: 'pointer', whiteSpace: 'nowrap',
              }}>
              Start workout →
            </button>
            <button
              onClick={() => onOpenDay(nextDayIndex)}
              style={{
                padding: '12px 14px', background: 'rgba(0,0,0,0.18)', color: 'var(--accent-fg)',
                border: 'none', borderRadius: 12, cursor: 'pointer',
              }}>
              <Icon.swap width={20} height={20} />
            </button>
          </div>
        </div>
      </div>

      {/* Calendar */}
      <div style={{ padding: '4px 22px 8px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 12 }}>
          <div className="display" style={{ fontSize: 22 }}>{monthName}</div>
          <span style={{ fontSize: 12, color: 'var(--fg-2)' }}>{workoutLog.length} sessions</span>
        </div>

        <div className="cal-grid" style={{ marginBottom: 8 }}>
          {['S', 'M', 'T', 'W', 'T', 'F', 'S'].map((w, i) => (
            <div key={i} style={{ fontSize: 10, color: 'var(--fg-3)', textAlign: 'center', letterSpacing: '0.1em', textTransform: 'uppercase' }}>{w}</div>
          ))}
        </div>
        <div className="cal-grid">
          {cells.map((c, i) => {
            if (!c) return <div key={i} />;
            const isToday = c.iso === TODAY_ISO;
            const dayNum = logByDate[c.iso];
            const done = !!dayNum;
            return (
              <div
                key={i}
                onClick={() => {
                  if (isToday) onOpenDay(nextDayIndex);
                  else if (done) onOpenDay(dayNum - 1);
                }}
                className={`cal-cell ${done ? 'done' : ''} ${isToday ? 'today' : ''}`}>
                <div style={{ fontFamily: done ? 'Anton' : 'Inter', fontSize: done ? 16 : 13 }}>{c.d}</div>
                {done && <div style={{ fontSize: 8, fontWeight: 700, opacity: 0.7, marginTop: 1 }}>D{dayNum}</div>}
                {isToday && !done && <div className="dot" />}
              </div>
            );
          })}
        </div>

        <div style={{ display: 'flex', gap: 12, marginTop: 12, fontSize: 11, color: 'var(--fg-2)' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 10, height: 10, borderRadius: 3, background: 'var(--accent)' }} /> Completed
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 10, height: 10, borderRadius: 3, border: '2px solid var(--accent)' }} /> Today
          </span>
        </div>
      </div>

      {/* This week's schedule */}
      <div style={{ padding: '20px 22px 0' }}>
        <div className="eyebrow" style={{ marginBottom: 10 }}>This week</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {WORKOUT_DAYS.map((d, i) => {
            const isNext = i === nextDayIndex;
            const completed = workoutLog.some(l => {
              const diff = (today - parseLocalISO(l.date)) / (1000 * 60 * 60 * 24);
              return diff <= 7 && diff >= 0 && l.day === i + 1;
            });
            return (
              <div
                key={d.id}
                onClick={() => onOpenDay(i)}
                className="row-tap"
                style={{
                  display: 'flex', alignItems: 'center', gap: 14,
                  padding: '14px 16px', borderRadius: 14,
                  background: isNext ? 'color-mix(in oklch, var(--accent) 12%, var(--surface))' : 'var(--surface)',
                  border: `1px solid ${isNext ? 'var(--accent)' : 'var(--line)'}`,
                  cursor: 'pointer',
                }}>
                <div className="display" style={{ fontSize: 28, width: 36, color: isNext ? 'var(--accent)' : 'var(--fg-3)' }}>
                  {i + 1}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--fg)' }}>{d.name}</div>
                  <div style={{ fontSize: 11, color: 'var(--fg-2)', marginTop: 2 }}>
                    {d.exercises.length} exercises · {d.exercises.reduce((s, e) => s + e.sets, 0)} sets
                  </div>
                </div>
                {completed && <Icon.check width={20} height={20} style={{ color: 'var(--accent)' }} />}
                {isNext && !completed && (
                  <span className="chip solid" style={{ fontSize: 10, padding: '4px 8px' }}>Next</span>
                )}
                {!isNext && !completed && <Icon.chev width={18} height={18} style={{ color: 'var(--fg-3)' }} />}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function StatPill({ label, value, icon }) {
  const IconComp = Icon[icon] || Icon.check;
  return (
    <div style={{
      flex: 1, background: 'var(--surface)',
      border: '1px solid var(--line)',
      borderRadius: 14, padding: '12px 14px',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <IconComp width={12} height={12} style={{ color: 'var(--accent)' }} />
        <span style={{ fontSize: 10, color: 'var(--fg-2)', textTransform: 'uppercase', letterSpacing: '0.08em' }}>{label}</span>
      </div>
      <div className="display" style={{ fontSize: 22, marginTop: 6, color: 'var(--fg)' }}>{value}</div>
    </div>
  );
}

// ---------- Day overview screen (shown before starting / via override) ----------
function DayOverview({ dayIndex, onStart, onSwitchDay, onBack, onSwapExercise }) {
  const day = WORKOUT_DAYS[dayIndex];
  const [tab, setTab] = React.useState('overview'); // 'overview' | 'warmup'
  const [switchOpen, setSwitchOpen] = React.useState(false);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <TopBar
        title=""
        left={<IconBtn icon="back" onClick={onBack} />}
        right={<IconBtn icon="more" onClick={() => setSwitchOpen(true)} />}
      />
      <div style={{ padding: '0 22px 14px' }}>
        <div className="eyebrow">Day {dayIndex + 1} of 5</div>
        <div className="display" style={{ fontSize: 44, marginTop: 6, lineHeight: 0.85 }}>{day.name}</div>
        <div style={{ display: 'flex', gap: 6, marginTop: 12 }}>
          <span className="chip outline" style={{ fontSize: 11 }}>{day.exercises.length} exercises</span>
          <span className="chip outline" style={{ fontSize: 11 }}>~{day.exercises.length * 11} min</span>
        </div>
      </div>

      <div style={{ padding: '0 22px 12px' }}>
        <div style={{ display: 'flex', gap: 6, background: 'var(--surface-2)', padding: 4, borderRadius: 10 }}>
          {[['overview', 'Exercises'], ['warmup', 'Warmup']].map(([id, label]) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              style={{
                flex: 1, padding: '10px 0',
                background: tab === id ? 'var(--bg)' : 'transparent',
                color: tab === id ? 'var(--fg)' : 'var(--fg-2)',
                border: 'none', borderRadius: 8,
                fontFamily: 'Inter', fontWeight: 600, fontSize: 13,
                cursor: 'pointer',
              }}>
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="scrollable" style={{ padding: '8px 22px 16px' }}>
        {tab === 'overview' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {day.exercises.map((ex, i) => {
              const def = EXERCISES[ex.id];
              return (
                <div key={i} className="card" style={{ display: 'flex', gap: 12, alignItems: 'center', padding: 12 }}>
                  <div className="display" style={{ fontSize: 26, width: 28, color: 'var(--fg-3)' }}>{i + 1}</div>
                  <div style={{ width: 48, height: 48, borderRadius: 8, flexShrink: 0 }} className="striped">
                    <div className="striped-label" style={{ fontSize: 7 }}>gif</div>
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14, fontWeight: 600 }}>{def.name}</div>
                    <div style={{ fontSize: 11, color: 'var(--fg-2)', marginTop: 2 }}>{def.primary.join(', ')}</div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 2 }}>
                    <div className="display" style={{ fontSize: 18, color: 'var(--accent)' }}>{ex.sets}×{ex.reps}</div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {tab === 'warmup' && (
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 16, background: 'var(--surface)', borderRadius: 14, border: '1px solid var(--line)', marginBottom: 14 }}>
              <Icon.flame width={28} height={28} style={{ color: 'var(--accent)', flexShrink: 0 }} />
              <div>
                <div style={{ fontSize: 14, fontWeight: 600 }}>Recommended warmup</div>
                <div style={{ fontSize: 11, color: 'var(--fg-2)', marginTop: 2 }}>~8 min · primes the muscles you'll hit today</div>
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {day.warmup.map((w, i) => (
                <div key={i} className="card-flat" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 14 }}>
                  <div className="display" style={{ fontSize: 22, color: 'var(--accent)', width: 24 }}>{i + 1}</div>
                  <div style={{ flex: 1, fontSize: 14 }}>{w}</div>
                  <Icon.check width={18} height={18} style={{ color: 'var(--fg-3)' }} />
                </div>
              ))}
            </div>
            <div style={{ marginTop: 14, fontSize: 12, color: 'var(--fg-3)', textAlign: 'center' }}>
              Skip warmup at your own risk.
            </div>
          </div>
        )}
      </div>

      <div style={{ padding: '12px 22px 16px', borderTop: '1px solid var(--line)' }}>
        <button className="btn-cta-huge" onClick={() => onStart(dayIndex)}>
          {tab === 'warmup' ? 'Start warmup →' : 'Start workout →'}
        </button>
      </div>

      {switchOpen && (
        <SwitchDaySheet
          currentDay={dayIndex}
          onPick={(i) => { onSwitchDay(i); setSwitchOpen(false); }}
          onClose={() => setSwitchOpen(false)}
        />
      )}
    </div>
  );
}

function SwitchDaySheet({ currentDay, onPick, onClose }) {
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" onClick={e => e.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="display" style={{ fontSize: 26, marginBottom: 4 }}>Override today</div>
        <div style={{ fontSize: 12, color: 'var(--fg-2)', marginBottom: 14 }}>Feeling like a different day? Switch up.</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {WORKOUT_DAYS.map((d, i) => (
            <div
              key={d.id}
              onClick={() => onPick(i)}
              className="row-tap"
              style={{
                padding: '14px 16px', borderRadius: 12,
                background: i === currentDay ? 'color-mix(in oklch, var(--accent) 12%, var(--surface-2))' : 'var(--surface-2)',
                display: 'flex', alignItems: 'center', gap: 12,
              }}>
              <div className="display" style={{ fontSize: 20, width: 28, color: i === currentDay ? 'var(--accent)' : 'var(--fg-3)' }}>{i + 1}</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, fontWeight: 600 }}>{d.name}</div>
                <div style={{ fontSize: 11, color: 'var(--fg-2)' }}>{d.exercises.length} exercises</div>
              </div>
              {i === currentDay && <span className="chip solid" style={{ fontSize: 10 }}>Current</span>}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { HomeScreen, DayOverview, StatPill, SwitchDaySheet });
