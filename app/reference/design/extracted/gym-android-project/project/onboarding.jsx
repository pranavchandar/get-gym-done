// onboarding.jsx — Splash, split selection, routine method, customize routine

function Splash({ onStart }) {
  return (
    <div className="scrollable" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: 24 }}>
      <div style={{ paddingTop: 80 }}>
        <div className="eyebrow" style={{ color: 'var(--accent)' }}>v1.0 — beta</div>
        <div className="display" style={{ fontSize: 88, color: 'var(--fg)', marginTop: 18, lineHeight: 0.85 }}>
          Get<br/>Gym<br/><span style={{ color: 'var(--accent)' }}>Done.</span>
        </div>
        <div style={{ marginTop: 26, fontSize: 16, color: 'var(--fg-2)', maxWidth: 280, lineHeight: 1.4 }}>
          A logbook that knows what day it is, what's next, and how strong you're getting.
        </div>
      </div>

      <div style={{ marginBottom: 12 }}>
        <div style={{ height: 180, borderRadius: 18, marginBottom: 24, position: 'relative', overflow: 'hidden' }} className="striped">
          <div className="striped-label">hero — athlete</div>
        </div>
        <button className="btn-cta-huge" onClick={onStart}>
          Sign up / Sign in
        </button>
        <button
          onClick={onStart}
          style={{
            width: '100%',
            marginTop: 10,
            padding: '16px 24px',
            borderRadius: 14,
            background: 'transparent',
            color: 'var(--fg)',
            border: '1.5px solid var(--line)',
            fontFamily: 'Anton, sans-serif',
            fontSize: 18,
            letterSpacing: '0.04em',
            textTransform: 'uppercase',
            cursor: 'pointer',
            transition: 'border-color 120ms ease, color 120ms ease',
          }}>
          Skip — keep it local
        </button>
        <div style={{ textAlign: 'center', marginTop: 12, color: 'var(--fg-3)', fontSize: 11, lineHeight: 1.45, maxWidth: 280, marginLeft: 'auto', marginRight: 'auto' }}>
          Sign in to sync across devices · Skip to start logging now, all data stays on this phone
        </div>
      </div>
    </div>
  );
}

function SplitSelect({ onNext, onBack, selected, setSelected }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <TopBar
        title="Pick your split"
        sub="Step 1 of 3"
        left={<IconBtn icon="back" onClick={onBack} />}
        big
      />
      <div style={{ padding: '0 20px 8px' }}>
        <div className="progress" style={{ marginBottom: 14 }}><div style={{ width: '33%' }} /></div>
        <p style={{ fontSize: 14, color: 'var(--fg-2)', margin: '0 0 8px' }}>
          The split decides what muscles you train on which day. You can change this anytime.
        </p>
      </div>
      <div className="scrollable" style={{ padding: '8px 20px 20px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {SPLIT_OPTIONS.map(opt => {
            const isSel = selected === opt.id;
            return (
              <div
                key={opt.id}
                className="row-tap"
                onClick={() => { setSelected(opt.id); setTimeout(onNext, 180); }}
                style={{
                  background: isSel ? 'color-mix(in oklch, var(--accent) 12%, var(--surface))' : 'var(--surface)',
                  border: `1.5px solid ${isSel ? 'var(--accent)' : 'var(--line)'}`,
                  borderRadius: 14,
                  padding: 16,
                  position: 'relative',
                }}>
                {opt.recommended && (
                  <div style={{ position: 'absolute', top: -8, right: 14, background: 'var(--accent)', color: 'var(--accent-fg)', fontSize: 9, fontWeight: 700, padding: '3px 8px', borderRadius: 100, textTransform: 'uppercase', letterSpacing: '0.1em' }}>
                    From your PDF
                  </div>
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <div className="display" style={{ fontSize: 22, color: 'var(--fg)', lineHeight: 1.02 }}>{opt.name}</div>
                    <div style={{ fontSize: 12, color: 'var(--fg-2)', marginTop: 6 }}>{opt.sub}</div>
                  </div>
                  <div style={{
                    width: 22, height: 22, borderRadius: 100,
                    border: `2px solid ${isSel ? 'var(--accent)' : 'var(--fg-3)'}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    {isSel && <div style={{ width: 10, height: 10, borderRadius: 100, background: 'var(--accent)' }} />}
                  </div>
                </div>
                {opt.days.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 12 }}>
                    {opt.days.map((d, i) => (
                      <div key={i} className="chip" style={{ background: 'var(--surface-2)', fontSize: 11 }}>
                        D{i + 1} · {d}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
        <div style={{ textAlign: 'center', marginTop: 16, fontSize: 11, color: 'var(--fg-3)', letterSpacing: '0.04em' }}>
          Tap a split to continue
        </div>
      </div>
    </div>
  );
}

function MethodSelect({ onNext, onBack, method, setMethod }) {
  const options = [
    {
      id: 'curated',
      title: 'Curate for me',
      sub: 'Use the proven 5-day program from your PDF, with optimal exercise order.',
      tag: 'Recommended',
      bullets: ['26 exercises preloaded', 'Form cues on every screen', 'Edit any time'],
    },
    {
      id: 'custom',
      title: 'Build my own',
      sub: 'Pick the exercises for each day. We tell you which muscles each one targets.',
      tag: 'Advanced',
      bullets: ['Drag to reorder', 'Search 200+ exercises', 'Save as template'],
    },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <TopBar
        title="Routine"
        sub="Step 2 of 3"
        left={<IconBtn icon="back" onClick={onBack} />}
        big
      />
      <div style={{ padding: '0 20px 16px' }}>
        <div className="progress" style={{ marginBottom: 14 }}><div style={{ width: '66%' }} /></div>
        <p style={{ fontSize: 14, color: 'var(--fg-2)', margin: 0 }}>
          How do you want to set up your exercises?
        </p>
      </div>
      <div className="scrollable" style={{ padding: '0 20px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {options.map(opt => {
          const isSel = method === opt.id;
          return (
            <div
              key={opt.id}
              className="row-tap"
              onClick={() => setMethod(opt.id)}
              style={{
                background: isSel ? 'var(--surface)' : 'var(--surface)',
                border: `2px solid ${isSel ? 'var(--accent)' : 'var(--line)'}`,
                borderRadius: 18,
                padding: 18,
                position: 'relative',
              }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
                <span className="chip" style={{ background: isSel ? 'var(--accent)' : 'var(--surface-2)', color: isSel ? 'var(--accent-fg)' : 'var(--fg-2)', fontSize: 10, padding: '4px 8px' }}>
                  {opt.tag}
                </span>
              </div>
              <div className="display" style={{ fontSize: 30, color: 'var(--fg)' }}>{opt.title}</div>
              <div style={{ fontSize: 14, color: 'var(--fg-2)', marginTop: 8, lineHeight: 1.45 }}>{opt.sub}</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 14 }}>
                {opt.bullets.map((b, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--fg-2)' }}>
                    <Icon.check width={14} height={14} style={{ color: 'var(--accent)' }} />
                    {b}
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
      <div style={{ padding: '12px 20px 16px', borderTop: '1px solid var(--line)' }}>
        <button className="btn-cta-huge" disabled={!method} onClick={onNext} style={{ opacity: method ? 1 : 0.4 }}>
          {method === 'curated' ? 'Use this program →' : method === 'custom' ? 'Build it →' : 'Continue →'}
        </button>
      </div>
    </div>
  );
}

function CustomizeRoutine({ onNext, onBack, customDays, setCustomDays }) {
  // For "Build my own" — pick exercises per day. We pre-populate with the curated days
  // so the user just edits, can swap exercises via the muscle picker.
  const [activeDay, setActiveDay] = React.useState(0);
  const [pickerOpen, setPickerOpen] = React.useState(false);

  const day = customDays[activeDay];
  const allExerciseIds = Object.keys(EXERCISES);

  const togglePick = (exId) => {
    const cur = day.exercises || [];
    const has = cur.find(e => e.id === exId);
    const next = has ? cur.filter(e => e.id !== exId) : [...cur, { id: exId, sets: 3, reps: '8-12' }];
    const newDays = [...customDays];
    newDays[activeDay] = { ...day, exercises: next };
    setCustomDays(newDays);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <TopBar
        title="Your routine"
        sub="Step 3 of 3"
        left={<IconBtn icon="back" onClick={onBack} />}
        big
      />
      <div style={{ padding: '0 20px 12px' }}>
        <div className="progress" style={{ marginBottom: 14 }}><div style={{ width: '100%' }} /></div>
        <div style={{ display: 'flex', gap: 6, overflowX: 'auto', paddingBottom: 4 }}>
          {customDays.map((d, i) => (
            <div
              key={i}
              onClick={() => setActiveDay(i)}
              className="row-tap"
              style={{
                padding: '8px 14px',
                borderRadius: 10,
                background: activeDay === i ? 'var(--accent)' : 'var(--surface-2)',
                color: activeDay === i ? 'var(--accent-fg)' : 'var(--fg)',
                fontFamily: 'Anton', fontSize: 14, letterSpacing: '0.04em',
                textTransform: 'uppercase', cursor: 'pointer', flexShrink: 0,
              }}>
              D{i + 1}
            </div>
          ))}
        </div>
      </div>
      <div className="scrollable" style={{ padding: '4px 20px 16px' }}>
        <div className="display" style={{ fontSize: 30, marginTop: 8, marginBottom: 4 }}>{day.name}</div>
        <div style={{ fontSize: 12, color: 'var(--fg-2)', marginBottom: 16 }}>{(day.exercises || []).length} exercises · ~{(day.exercises || []).length * 12} min</div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {(day.exercises || []).map((ex, i) => {
            const def = EXERCISES[ex.id];
            return (
              <div key={ex.id + i} className="card row-tap" onClick={() => setPickerOpen(true)} style={{ padding: 12, display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer' }}>
                <div style={{ width: 44, height: 44, borderRadius: 8, flexShrink: 0 }} className="striped">
                  <div className="striped-label" style={{ fontSize: 7 }}>gif</div>
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--fg)' }}>{def.name}</div>
                  <div style={{ fontSize: 11, color: 'var(--fg-2)', marginTop: 2 }}>{def.primary.join(', ')}</div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 2 }}>
                  <span className="chip" style={{ fontSize: 10, padding: '3px 7px' }}>{ex.sets} × {ex.reps}</span>
                  <span style={{ fontSize: 10, color: 'var(--fg-3)' }}>tap to swap</span>
                </div>
              </div>
            );
          })}
          <button
            onClick={() => setPickerOpen(true)}
            style={{
              padding: 14, borderRadius: 12, border: '1.5px dashed var(--line)',
              background: 'transparent', color: 'var(--fg-2)', cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
              fontFamily: 'Inter', fontWeight: 600, fontSize: 14,
            }}>
            <Icon.plus width={16} height={16} /> Add exercise
          </button>
        </div>
      </div>
      <div style={{ padding: '12px 20px 16px', borderTop: '1px solid var(--line)' }}>
        <button className="btn-cta-huge" onClick={onNext}>
          Lock it in →
        </button>
      </div>

      {pickerOpen && (
        <ExercisePicker
          selectedIds={(day.exercises || []).map(e => e.id)}
          onToggle={togglePick}
          onClose={() => setPickerOpen(false)}
        />
      )}
    </div>
  );
}

function ExercisePicker({ selectedIds, onToggle, onClose }) {
  const [filter, setFilter] = React.useState('all');
  const muscles = ['Chest', 'Back', 'Lats', 'Shoulders', 'Biceps', 'Triceps', 'Quads', 'Hamstrings', 'Glutes', 'Calves', 'Abs'];
  const allEx = Object.values(EXERCISES);
  const filtered = filter === 'all'
    ? allEx
    : allEx.filter(e => e.primary.includes(filter) || e.secondary.includes(filter));

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" onClick={e => e.stopPropagation()} style={{ height: '85%' }}>
        <div className="sheet-handle" />
        <div className="display" style={{ fontSize: 28, marginBottom: 4 }}>Add exercise</div>
        <div style={{ fontSize: 12, color: 'var(--fg-2)', marginBottom: 14 }}>{selectedIds.length} picked · tap to add/remove</div>

        <div style={{ display: 'flex', gap: 6, overflowX: 'auto', marginBottom: 14, paddingBottom: 4 }}>
          {['all', ...muscles].map(m => (
            <div
              key={m}
              onClick={() => setFilter(m)}
              className="chip"
              style={{
                background: filter === m ? 'var(--accent)' : 'var(--surface-2)',
                color: filter === m ? 'var(--accent-fg)' : 'var(--fg-2)',
                cursor: 'pointer', flexShrink: 0,
              }}>
              {m}
            </div>
          ))}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {filtered.map(ex => {
            const isSel = selectedIds.includes(ex.id);
            return (
              <div
                key={ex.id}
                onClick={() => onToggle(ex.id)}
                className="row-tap"
                style={{
                  display: 'flex', alignItems: 'center', gap: 12,
                  padding: 10, borderRadius: 12,
                  background: isSel ? 'color-mix(in oklch, var(--accent) 14%, var(--surface))' : 'var(--surface-2)',
                  border: `1.5px solid ${isSel ? 'var(--accent)' : 'transparent'}`,
                  cursor: 'pointer',
                }}>
                <MuscleMap active={[...ex.primary, ...ex.secondary]} width={44} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--fg)' }}>{ex.name}</div>
                  <div style={{ fontSize: 11, color: 'var(--fg-2)', marginTop: 2 }}>{ex.primary.join(', ')}{ex.secondary.length > 0 ? ' · ' + ex.secondary.join(', ') : ''}</div>
                </div>
                <div style={{
                  width: 24, height: 24, borderRadius: 100,
                  background: isSel ? 'var(--accent)' : 'transparent',
                  border: `2px solid ${isSel ? 'var(--accent)' : 'var(--fg-3)'}`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  {isSel && <Icon.check width={12} height={12} style={{ color: 'var(--accent-fg)' }} />}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { Splash, SplitSelect, MethodSelect, CustomizeRoutine, ExercisePicker });
