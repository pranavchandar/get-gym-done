// app.jsx — Main App: state, routing between screens, tweaks integration.

// ---------- Tweak defaults (persisted via host) ----------
const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "palette": "lime",
  "theme": "dark",
  "layout": "stacked",
  "unit": "kg",
  "restTimerEnabled": true,
  "showMuscleMap": true,
  "fontSize": "regular"
}/*EDITMODE-END*/;

// Palette presets
const PALETTES = {
  lime:    { accent: 'oklch(0.88 0.22 125)', accentFg: '#0a0a09', accent2: 'oklch(0.70 0.22 25)' },
  ember:   { accent: 'oklch(0.74 0.20 50)',  accentFg: '#0a0a09', accent2: 'oklch(0.72 0.18 350)' },
  electric:{ accent: 'oklch(0.80 0.18 235)', accentFg: '#0a0a09', accent2: 'oklch(0.78 0.18 140)' },
  blood:   { accent: 'oklch(0.62 0.22 25)',  accentFg: '#f7f5f0', accent2: 'oklch(0.85 0.18 90)' },
  mono:    { accent: '#f7f5f0',              accentFg: '#0a0a09', accent2: '#a8a59a' },
};

function applyTweaks(t) {
  const root = document.documentElement;
  root.setAttribute('data-theme', t.theme);
  const p = PALETTES[t.palette] || PALETTES.lime;
  root.style.setProperty('--accent', p.accent);
  root.style.setProperty('--accent-fg', p.accentFg);
  root.style.setProperty('--accent-2', p.accent2);
  // font size
  const baseSize = t.fontSize === 'large' ? 17 : 16;
  document.body.style.fontSize = baseSize + 'px';
}

// ---------- Onboarding state machine ----------
const SCREENS = {
  SPLASH: 'splash',
  SPLIT: 'split',
  METHOD: 'method',
  CUSTOMIZE: 'customize',
  APP: 'app',
};

function App() {
  const [tweaks, setTweak] = useTweaks(TWEAK_DEFAULTS);
  React.useEffect(() => { applyTweaks(tweaks); }, [tweaks]);

  // Onboarding state
  const [screen, setScreen] = React.useState(SCREENS.SPLASH);
  const [splitChoice, setSplitChoice] = React.useState('5day-illustrated');
  const [method, setMethod] = React.useState('curated');
  const [customDays, setCustomDays] = React.useState(WORKOUT_DAYS);

  // App-shell state
  const [tab, setTab] = React.useState('home');
  const [appView, setAppView] = React.useState('home'); // home | day-overview | workout | complete
  const [openDayIdx, setOpenDayIdx] = React.useState(0);
  const [workoutLog, setWorkoutLog] = React.useState(WORKOUT_LOG);

  // Next-day logic: cycle through 1..5 based on last log
  const nextDayIndex = React.useMemo(() => {
    if (workoutLog.length === 0) return 0;
    const last = workoutLog[workoutLog.length - 1];
    return last.day % 5; // last was day 5 -> next is 0
  }, [workoutLog]);

  const startWorkout = (idx) => {
    setOpenDayIdx(idx);
    setAppView('workout');
  };

  const onWorkoutComplete = (dayIdx) => {
    // Log it
    setWorkoutLog([...workoutLog, { date: TODAY_ISO, day: dayIdx + 1 }]);
    setAppView('complete');
  };

  // ─── Render ────────────────────────────────────────────────────────

  let content;
  if (screen === SCREENS.SPLASH) {
    content = <Splash onStart={() => setScreen(SCREENS.SPLIT)} />;
  } else if (screen === SCREENS.SPLIT) {
    content = <SplitSelect
      selected={splitChoice}
      setSelected={setSplitChoice}
      onBack={() => setScreen(SCREENS.SPLASH)}
      onNext={() => setScreen(SCREENS.METHOD)}
    />;
  } else if (screen === SCREENS.METHOD) {
    content = <MethodSelect
      method={method}
      setMethod={setMethod}
      onBack={() => setScreen(SCREENS.SPLIT)}
      onNext={() => {
        if (method === 'custom') setScreen(SCREENS.CUSTOMIZE);
        else setScreen(SCREENS.APP);
      }}
    />;
  } else if (screen === SCREENS.CUSTOMIZE) {
    content = <CustomizeRoutine
      customDays={customDays}
      setCustomDays={setCustomDays}
      onBack={() => setScreen(SCREENS.METHOD)}
      onNext={() => setScreen(SCREENS.APP)}
    />;
  } else {
    // Main app shell
    if (appView === 'workout') {
      content = <WorkoutSession
        key={openDayIdx + '-' + tweaks.layout}
        dayIndex={openDayIdx}
        unit={tweaks.unit}
        layoutVariant={tweaks.layout}
        restTimerEnabled={tweaks.restTimerEnabled}
        showMuscleMap={tweaks.showMuscleMap}
        onExit={() => setAppView('home')}
        onComplete={onWorkoutComplete}
      />;
    } else if (appView === 'complete') {
      content = <WorkoutComplete dayIndex={openDayIdx} onDone={() => { setAppView('home'); setTab('home'); }} />;
    } else if (appView === 'day-overview') {
      content = <DayOverview
        dayIndex={openDayIdx}
        onBack={() => setAppView('home')}
        onStart={startWorkout}
        onSwitchDay={(i) => setOpenDayIdx(i)}
      />;
    } else {
      // Tab content
      let tabContent;
      if (tab === 'home') {
        tabContent = <HomeScreen
          workoutLog={workoutLog}
          nextDayIndex={nextDayIndex}
          unit={tweaks.unit}
          onStartWorkout={startWorkout}
          onOpenDay={(i) => { setOpenDayIdx(i); setAppView('day-overview'); }}
        />;
      } else if (tab === 'workouts') {
        tabContent = <WorkoutsList
          workoutLog={workoutLog}
          onOpenDay={(i) => { setOpenDayIdx(i); setAppView('day-overview'); }}
          unit={tweaks.unit}
        />;
      } else if (tab === 'profile') {
        tabContent = <ProfileScreen unit={tweaks.unit} />;
      } else if (tab === 'settings') {
        tabContent = <SettingsScreen
          unit={tweaks.unit}
          theme={tweaks.theme}
          splitName={SPLIT_OPTIONS.find(s => s.id === splitChoice)?.name || '5 Day'}
          onEditRoutine={() => setScreen(SCREENS.CUSTOMIZE)}
          onResetOnboarding={() => setScreen(SCREENS.SPLIT)}
        />;
      }
      content = (
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            {tabContent}
          </div>
          <TabBar current={tab} onChange={setTab} />
        </div>
      );
    }
  }

  // Show statusbar conditionally — full bleed flow during onboarding too
  return (
    <div className="phone-frame">
      <div className="phone-content">
        <PhoneStatusBar />
        <div key={screen + appView} className="screen-enter" style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          {content}
        </div>
        <GestureNav />
      </div>

      {/* Tweaks panel — exposes layouts, palettes, units */}
      <TweaksPanel title="Tweaks">
        <TweakSection label="Brand" />
        <TweakSelect label="Accent" value={tweaks.palette} onChange={v => setTweak('palette', v)}
          options={[
            { value: 'lime', label: 'Electric lime' },
            { value: 'ember', label: 'Ember orange' },
            { value: 'electric', label: 'Electric blue' },
            { value: 'blood', label: 'Blood red' },
            { value: 'mono', label: 'Mono (no accent)' },
          ]} />
        <TweakRadio label="Theme" value={tweaks.theme} onChange={v => setTweak('theme', v)}
          options={[{value:'dark',label:'Dark'},{value:'light',label:'Light'}]} />

        <TweakSection label="Workout screen" />
        <TweakSelect label="Layout variant" value={tweaks.layout} onChange={v => setTweak('layout', v)}
          options={[
            { value: 'stacked', label: 'Stacked — classic vertical' },
            { value: 'focus', label: 'Focus — huge numerals' },
            { value: 'coach', label: 'Coach — cue-first' },
            { value: 'compact', label: 'Compact — everything visible' },
          ]} />
        <TweakToggle label="Auto-start rest timer"
          value={tweaks.restTimerEnabled} onChange={v => setTweak('restTimerEnabled', v)} />
        <TweakToggle label="Show muscle map"
          value={tweaks.showMuscleMap} onChange={v => setTweak('showMuscleMap', v)} />

        <TweakSection label="Preferences" />
        <TweakRadio label="Unit" value={tweaks.unit} onChange={v => setTweak('unit', v)}
          options={[{value:'kg',label:'KG'},{value:'lbs',label:'LBS'}]} />
        <TweakRadio label="Type size" value={tweaks.fontSize} onChange={v => setTweak('fontSize', v)}
          options={[{value:'regular',label:'Regular'},{value:'large',label:'Large'}]} />

        <TweakSection label="Demo jump-to" />
        <TweakButton label="→ Active workout"
          onClick={() => { setScreen(SCREENS.APP); setAppView('workout'); setOpenDayIdx(0); }} />
        <TweakButton label="→ Day overview"
          onClick={() => { setScreen(SCREENS.APP); setAppView('day-overview'); setOpenDayIdx(0); }} />
        <TweakButton label="→ Profile / metrics"
          onClick={() => { setScreen(SCREENS.APP); setAppView('home'); setTab('profile'); }} />
        <TweakButton label="↺ Restart onboarding"
          onClick={() => setScreen(SCREENS.SPLASH)} />
      </TweaksPanel>
    </div>
  );
}

// ─── Workouts list (small extra view for "Workouts" tab) ─────────────
function WorkoutsList({ onOpenDay, workoutLog, unit }) {
  return (
    <div className="scrollable" style={{ padding: '12px 22px 16px' }}>
      <div className="eyebrow">Routine</div>
      <div className="display" style={{ fontSize: 36, marginTop: 6, marginBottom: 16 }}>5 Day Illustrated</div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {WORKOUT_DAYS.map((d, i) => {
          const completedCount = workoutLog.filter(l => l.day === i + 1).length;
          return (
            <div key={d.id} className="card row-tap" onClick={() => onOpenDay(i)} style={{ padding: 14 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div className="display" style={{ fontSize: 36, width: 44, color: 'var(--accent)' }}>{i + 1}</div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 15, fontWeight: 700 }}>{d.name}</div>
                  <div style={{ fontSize: 11, color: 'var(--fg-2)', marginTop: 2 }}>{d.exercises.length} exercises · {d.exercises.reduce((s, e) => s + e.sets, 0)} sets</div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div className="display" style={{ fontSize: 18, color: 'var(--fg)' }}>{completedCount}×</div>
                  <div style={{ fontSize: 10, color: 'var(--fg-3)' }}>completed</div>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 4, marginTop: 12, overflowX: 'auto' }}>
                {d.exercises.slice(0, 6).map((ex, j) => {
                  const def = EXERCISES[ex.id];
                  return (
                    <div key={j} style={{
                      padding: '4px 8px', borderRadius: 6,
                      background: 'var(--surface-2)', fontSize: 10, color: 'var(--fg-2)',
                      whiteSpace: 'nowrap', flexShrink: 0,
                    }}>{def.name.split(' ').slice(0, 2).join(' ')}</div>
                  );
                })}
                {d.exercises.length > 6 && (
                  <div style={{ padding: '4px 8px', fontSize: 10, color: 'var(--fg-3)' }}>+{d.exercises.length - 6}</div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

Object.assign(window, { App, WorkoutsList, applyTweaks });
