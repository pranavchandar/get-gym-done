// workout.jsx — Active workout: exercise carousel + 4 layout variants + set tracking

function WorkoutSession({ dayIndex, onExit, unit, layoutVariant = 'stacked', onComplete, restTimerEnabled = true, showMuscleMap = true }) {
  const day = WORKOUT_DAYS[dayIndex];
  const [exIdx, setExIdx] = React.useState(0);
  const [sessionData, setSessionData] = React.useState(() =>
    day.exercises.map(ex => ({
      sets: Array.from({ length: ex.sets }, () => ({
        weight: LAST_WEIGHT[ex.id] || 20,
        reps: parseInt(ex.reps.split('-')[1]) || 8,
        done: false,
      })),
    }))
  );
  const [restTimer, setRestTimer] = React.useState(null); // seconds remaining
  const [toast, setToast] = React.useState(null);
  const [editTarget, setEditTarget] = React.useState(null); // {field: 'weight'|'reps', setIdx}

  // Rest timer countdown
  React.useEffect(() => {
    if (restTimer == null || restTimer <= 0) return;
    const t = setTimeout(() => setRestTimer(restTimer - 1), 1000);
    return () => clearTimeout(t);
  }, [restTimer]);

  const ex = day.exercises[exIdx];
  const exDef = EXERCISES[ex.id];
  const exSets = sessionData[exIdx].sets;
  const currentSetIdx = exSets.findIndex(s => !s.done);
  const isExDone = currentSetIdx === -1;

  const updateSet = (setIdx, patch) => {
    const next = sessionData.map((d, i) => {
      if (i !== exIdx) return d;
      return { ...d, sets: d.sets.map((s, j) => j === setIdx ? { ...s, ...patch } : s) };
    });
    setSessionData(next);
  };

  const completeSet = () => {
    if (currentSetIdx === -1) return;
    updateSet(currentSetIdx, { done: true });
    if (restTimerEnabled) {
      setRestTimer(90);
      setToast(`Set ${currentSetIdx + 1} logged · 90s rest`);
    } else {
      setToast(`Set ${currentSetIdx + 1} logged`);
    }
    setTimeout(() => setToast(null), 1800);
  };

  const undoLastSet = () => {
    const lastDone = exSets.map((s, i) => ({ s, i })).filter(x => x.s.done).pop();
    if (!lastDone) return;
    updateSet(lastDone.i, { done: false });
    setRestTimer(null);
  };

  const addSet = () => {
    const last = exSets[exSets.length - 1];
    const newSet = last
      ? { weight: last.weight, reps: last.reps, done: false }
      : { weight: LAST_WEIGHT[ex.id] || 20, reps: 8, done: false };
    const next = sessionData.map((d, i) =>
      i !== exIdx ? d : { ...d, sets: [...d.sets, newSet] }
    );
    setSessionData(next);
    setToast(`Set ${exSets.length + 1} added`);
    setTimeout(() => setToast(null), 1500);
  };

  const removeSet = (setIdx) => {
    if (exSets.length <= 1) return;
    const next = sessionData.map((d, i) =>
      i !== exIdx ? d : { ...d, sets: d.sets.filter((_, j) => j !== setIdx) }
    );
    setSessionData(next);
  };

  const goToNextExercise = () => {
    if (exIdx < day.exercises.length - 1) {
      setExIdx(exIdx + 1);
      setRestTimer(null);
    } else {
      onComplete && onComplete(dayIndex);
    }
  };

  const totalSetsCompleted = sessionData.reduce((s, d) => s + d.sets.filter(x => x.done).length, 0);
  const totalSets = sessionData.reduce((s, d) => s + d.sets.length, 0);

  const layoutProps = {
    day, ex, exDef, exIdx, exSets, currentSetIdx, isExDone, dayIndex,
    completeSet, undoLastSet, updateSet, addSet, removeSet,
    goToNextExercise, setEditTarget,
    unit, onExit, setExIdx, totalSetsCompleted, totalSets,
    sessionData, showMuscleMap,
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      {restTimer != null && restTimer > 0 && (
        <RestBar seconds={restTimer} onSkip={() => setRestTimer(null)} />
      )}
      {layoutVariant === 'stacked' && <LayoutStacked {...layoutProps} />}
      {layoutVariant === 'focus' && <LayoutFocus {...layoutProps} />}
      {layoutVariant === 'coach' && <LayoutCoach {...layoutProps} />}
      {layoutVariant === 'compact' && <LayoutCompact {...layoutProps} />}

      {toast && <div className="toast">{toast}</div>}

      {editTarget && (
        <KeypadSheet
          field={editTarget.field}
          value={exSets[editTarget.setIdx][editTarget.field]}
          unit={unit}
          onSave={(v) => { updateSet(editTarget.setIdx, { [editTarget.field]: v }); setEditTarget(null); }}
          onClose={() => setEditTarget(null)}
        />
      )}
    </div>
  );
}

// ─── Top bar shared across variants ──────────────────────────────────
function WorkoutTopBar({ ex, exIdx, total, dayName, onExit, onSkip, totalSetsCompleted, totalSets }) {
  const progress = totalSets > 0 ? (totalSetsCompleted / totalSets) * 100 : 0;
  const [menuOpen, setMenuOpen] = React.useState(false);
  return (
    <div style={{ padding: '8px 18px 12px', background: 'var(--bg)', position: 'relative' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <IconBtn icon="close" onClick={onExit} />
        <div style={{ textAlign: 'center' }}>
          <div className="eyebrow" style={{ fontSize: 10 }}>{dayName}</div>
          <div style={{ fontFamily: 'Anton', fontSize: 18, marginTop: 2 }}>{exIdx + 1} / {total}</div>
        </div>
        <IconBtn icon="more" onClick={() => setMenuOpen(o => !o)} />
      </div>
      <div className="progress"><div style={{ width: progress + '%' }} /></div>

      {menuOpen && (
        <React.Fragment>
          <div onClick={() => setMenuOpen(false)} style={{ position: 'fixed', inset: 0, zIndex: 40 }} />
          <div style={{
            position: 'absolute', right: 18, top: 50, zIndex: 41,
            background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 12,
            boxShadow: '0 14px 40px rgba(0,0,0,0.4)', overflow: 'hidden', minWidth: 180,
          }}>
            {onSkip && (
              <div className="row-tap" onClick={() => { setMenuOpen(false); onSkip(); }}
                style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '13px 16px', fontSize: 14, fontWeight: 500, color: 'var(--fg)' }}>
                <Icon.arrow width={16} height={16} style={{ color: 'var(--fg-2)' }} /> Skip exercise
              </div>
            )}
            <div style={{ height: 1, background: 'var(--line)' }} />
            <div className="row-tap" onClick={() => { setMenuOpen(false); onExit(); }}
              style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '13px 16px', fontSize: 14, fontWeight: 500, color: 'var(--danger)' }}>
              <Icon.close width={16} height={16} /> End workout
            </div>
          </div>
        </React.Fragment>
      )}
    </div>
  );
}

// ─── Exercise carousel dots ───────────────────────────────────────────
function ExerciseDots({ items, current, sessionData, onPick }) {
  return (
    <div style={{ display: 'flex', gap: 4, padding: '4px 18px 8px', overflowX: 'auto' }}>
      {items.map((it, i) => {
        const data = sessionData ? sessionData[i] : null;
        const done = data && data.sets.every(s => s.done);
        const active = i === current;
        return (
          <div
            key={i}
            onClick={() => onPick(i)}
            style={{
              flex: 1, minWidth: 12, height: 4, borderRadius: 100,
              background: done ? 'var(--accent)' : active ? 'var(--fg)' : 'var(--surface-3)',
              cursor: 'pointer', flexShrink: 0,
            }} />
        );
      })}
    </div>
  );
}

// ─── Rest timer bar ────────────────────────────────────────────────────
function RestBar({ seconds, onSkip }) {
  return (
    <div style={{
      background: 'color-mix(in oklch, var(--accent) 22%, var(--surface))',
      borderBottom: '1px solid var(--accent)',
      padding: '10px 18px',
      display: 'flex', alignItems: 'center', gap: 10,
    }}>
      <Icon.timer width={20} height={20} style={{ color: 'var(--accent)' }} />
      <div style={{ flex: 1 }}>
        <div style={{ fontFamily: 'Anton', fontSize: 18, color: 'var(--fg)' }}>RESTING · {seconds}s</div>
      </div>
      <button
        onClick={onSkip}
        style={{
          background: 'transparent', border: '1px solid var(--accent)', color: 'var(--accent)',
          borderRadius: 8, padding: '6px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer',
        }}>
        Skip
      </button>
    </div>
  );
}

// =====================================================================
// LAYOUT 1 — STACKED (default)
// =====================================================================
function LayoutStacked(props) {
  const { day, ex, exDef, exIdx, exSets, currentSetIdx, isExDone,
    completeSet, undoLastSet, addSet, updateSet, goToNextExercise, setEditTarget,
    unit, onExit, setExIdx, totalSetsCompleted, totalSets, showMuscleMap, sessionData } = props;
  const session = exSets.map(s => s).slice(0, exSets.length); // sessionData passthrough not needed
  return (
    <>
      <WorkoutTopBar ex={ex} exIdx={exIdx} total={day.exercises.length}
        dayName={day.name} onExit={onExit} onSkip={goToNextExercise}
        totalSetsCompleted={totalSetsCompleted} totalSets={totalSets} />
      <ExerciseDots items={day.exercises} current={exIdx} sessionData={sessionData} onPick={setExIdx} />

      <div className="scrollable" style={{ padding: '8px 18px 18px' }}>
        {/* Exercise GIF placeholder */}
        <div style={{ height: 200, borderRadius: 16, marginBottom: 14, position: 'relative' }} className="striped">
          <div className="striped-label">exercise gif · {exDef.id}</div>
          <button style={{
            position: 'absolute', right: 12, bottom: 12,
            background: 'var(--bg)', border: '1px solid var(--line)', color: 'var(--fg)',
            borderRadius: 8, padding: '6px 10px', fontSize: 11, fontWeight: 600,
            display: 'flex', alignItems: 'center', gap: 4, cursor: 'pointer',
          }}>
            <Icon.play width={11} height={11} /> Demo
          </button>
        </div>

        {/* Exercise name + sets prescribed */}
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, marginBottom: 14 }}>
          <div style={{ flex: 1 }}>
            <div className="eyebrow">Exercise {exIdx + 1}</div>
            <div className="display" style={{ fontSize: 32, marginTop: 4, lineHeight: 0.9 }}>{exDef.name}</div>
          </div>
          <div style={{
            background: 'var(--surface)', border: '1px solid var(--line)',
            borderRadius: 10, padding: '8px 12px', textAlign: 'center',
          }}>
            <div className="display" style={{ fontSize: 22, color: 'var(--accent)' }}>{ex.sets}×{ex.reps}</div>
            <div style={{ fontSize: 9, color: 'var(--fg-3)', textTransform: 'uppercase', letterSpacing: '0.1em' }}>Prescription</div>
          </div>
        </div>

        {/* Muscles targeted */}
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', padding: 12, background: 'var(--surface)', borderRadius: 14, border: '1px solid var(--line)', marginBottom: 14 }}>
          {showMuscleMap && <MuscleMap active={[...exDef.primary, ...exDef.secondary]} width={56} />}
          <div style={{ flex: 1 }}>
            <div className="eyebrow">Targets</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 6 }}>
              {exDef.primary.map(m => (
                <span key={m} className="chip solid" style={{ fontSize: 10, padding: '3px 7px' }}>{m}</span>
              ))}
              {exDef.secondary.map(m => (
                <span key={m} className="chip outline" style={{ fontSize: 10, padding: '3px 7px' }}>{m}</span>
              ))}
            </div>
          </div>
        </div>

        {/* Form cues */}
        <div style={{ marginBottom: 18 }}>
          <div className="eyebrow" style={{ marginBottom: 8 }}>Form cues</div>
          <div className="card" style={{ padding: 14 }}>
            {exDef.cues.map((c, i) => (
              <div key={i} style={{ display: 'flex', gap: 10, padding: '6px 0', fontSize: 13.5, color: 'var(--fg)' }}>
                <div style={{ color: 'var(--accent)', fontFamily: 'Anton', fontSize: 14 }}>{(i + 1).toString().padStart(2, '0')}</div>
                <div style={{ flex: 1 }}>{c}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Sets */}
        <div style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
            <div className="eyebrow">Sets</div>
            <span style={{ fontSize: 11, color: 'var(--fg-2)' }}>{exSets.filter(s => s.done).length} / {exSets.length} done</span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {exSets.map((s, i) => (
              <SetRow
                key={i}
                idx={i}
                set={s}
                active={i === currentSetIdx}
                unit={unit}
                onEditWeight={() => setEditTarget({ field: 'weight', setIdx: i })}
                onEditReps={() => setEditTarget({ field: 'reps', setIdx: i })}
                onAdjust={(setIdx, field, delta) => {
                  const v = exSets[setIdx][field] + delta;
                  updateSet(setIdx, { [field]: Math.max(0, Math.round(v * 100) / 100) });
                }}
              />
            ))}
            <AddSetButton onClick={addSet} />
          </div>
        </div>
      </div>

      <CompleteSetCTA isExDone={isExDone} onComplete={completeSet} onNext={goToNextExercise}
        onUndo={undoLastSet} canUndo={exSets.some(s => s.done)}
        currentSet={exSets[currentSetIdx]} unit={unit} setIdx={currentSetIdx} totalSets={exSets.length} />
    </>
  );
}

// =====================================================================
// LAYOUT 2 — FOCUS (minimal big numerals)
// =====================================================================
function LayoutFocus(props) {
  const { day, ex, exDef, exIdx, exSets, currentSetIdx, isExDone,
    completeSet, undoLastSet, addSet, updateSet, goToNextExercise, setEditTarget,
    unit, onExit, setExIdx, totalSetsCompleted, totalSets, showMuscleMap, sessionData } = props;
  const [moreOpen, setMoreOpen] = React.useState(false);
  const curSet = exSets[currentSetIdx] || exSets[exSets.length - 1];
  return (
    <>
      <WorkoutTopBar ex={ex} exIdx={exIdx} total={day.exercises.length}
        dayName={day.name} onExit={onExit} onSkip={goToNextExercise}
        totalSetsCompleted={totalSetsCompleted} totalSets={totalSets} />
      <ExerciseDots items={day.exercises} current={exIdx} sessionData={sessionData} onPick={setExIdx} />

      <div className="scrollable" style={{ padding: '0 18px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
        <div style={{ padding: '24px 0 12px', textAlign: 'center' }}>
          <div className="eyebrow">Exercise {exIdx + 1} of {day.exercises.length}</div>
          <div className="display" style={{ fontSize: 30, marginTop: 6, lineHeight: 0.9 }}>{exDef.name}</div>
          <div style={{ fontSize: 12, color: 'var(--fg-2)', marginTop: 6 }}>Set {Math.min(currentSetIdx + 1, exSets.length)} of {exSets.length} · {exDef.primary.join(', ')}</div>
        </div>

        {/* HUGE numerals */}
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 8, padding: '12px 0' }}>
          <div onClick={() => !isExDone && setEditTarget({ field: 'weight', setIdx: currentSetIdx })}
            style={{ flex: 1, textAlign: 'right', cursor: 'pointer' }}>
            <div className="huge-num" style={{ color: 'var(--fg)', fontSize: 110 }}>
              {isExDone ? curSet.weight : exSets[currentSetIdx].weight}
            </div>
            <div className="eyebrow" style={{ color: 'var(--accent)', marginTop: 4 }}>{unitLabel(unit)} · weight</div>
          </div>
          <div className="display" style={{ fontSize: 60, color: 'var(--fg-3)', alignSelf: 'flex-start', marginTop: 14 }}>×</div>
          <div onClick={() => !isExDone && setEditTarget({ field: 'reps', setIdx: currentSetIdx })}
            style={{ flex: 1, textAlign: 'left', cursor: 'pointer' }}>
            <div className="huge-num" style={{ color: 'var(--fg)', fontSize: 110 }}>
              {isExDone ? curSet.reps : exSets[currentSetIdx].reps}
            </div>
            <div className="eyebrow" style={{ color: 'var(--accent)', marginTop: 4 }}>reps</div>
          </div>
        </div>

        <div style={{ textAlign: 'center', fontSize: 11, color: 'var(--fg-3)', marginBottom: 12 }}>
          Tap a number to edit · target {ex.reps} reps
        </div>

        {/* Set chip row */}
        <div style={{ display: 'flex', gap: 6, justifyContent: 'center', marginBottom: 12 }}>
          {exSets.map((s, i) => (
            <div key={i} style={{
              width: 36, height: 36, borderRadius: 10,
              background: s.done ? 'var(--accent)' : i === currentSetIdx ? 'var(--surface-2)' : 'var(--surface)',
              color: s.done ? 'var(--accent-fg)' : i === currentSetIdx ? 'var(--fg)' : 'var(--fg-3)',
              border: i === currentSetIdx ? '2px solid var(--accent)' : '1px solid var(--line)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontFamily: 'Anton', fontSize: 16,
            }}>
              {s.done ? <Icon.check width={14} height={14} /> : i + 1}
            </div>
          ))}
        </div>

        {/* Expand for cues */}
        <button onClick={() => setMoreOpen(!moreOpen)}
          style={{
            background: 'transparent', border: 'none', color: 'var(--fg-2)',
            fontSize: 12, fontWeight: 600, padding: 8, cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4,
          }}>
          Form cues
          <Icon.chevDown width={14} height={14} style={{ transform: moreOpen ? 'rotate(180deg)' : 'none', transition: 'transform 200ms ease' }} />
        </button>
        {moreOpen && (
          <div className="card-flat" style={{ padding: 14, marginBottom: 12 }}>
            {exDef.cues.map((c, i) => (
              <div key={i} style={{ display: 'flex', gap: 10, padding: '4px 0', fontSize: 13 }}>
                <div style={{ color: 'var(--accent)', fontFamily: 'Anton' }}>{(i + 1).toString().padStart(2, '0')}</div>
                <div>{c}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <CompleteSetCTA isExDone={isExDone} onComplete={completeSet} onNext={goToNextExercise}
        onUndo={undoLastSet} canUndo={exSets.some(s => s.done)}
        currentSet={exSets[currentSetIdx]} unit={unit} setIdx={currentSetIdx} totalSets={exSets.length}
        compact />
    </>
  );
}

// =====================================================================
// LAYOUT 3 — COACH (cues elevated, no image)
// =====================================================================
function LayoutCoach(props) {
  const { day, ex, exDef, exIdx, exSets, currentSetIdx, isExDone,
    completeSet, undoLastSet, addSet, updateSet, goToNextExercise, setEditTarget,
    unit, onExit, setExIdx, totalSetsCompleted, totalSets, showMuscleMap, sessionData } = props;
  const [cueIdx, setCueIdx] = React.useState(0);
  React.useEffect(() => { setCueIdx(0); }, [exIdx]);
  return (
    <>
      <WorkoutTopBar ex={ex} exIdx={exIdx} total={day.exercises.length}
        dayName={day.name} onExit={onExit} onSkip={goToNextExercise}
        totalSetsCompleted={totalSetsCompleted} totalSets={totalSets} />
      <ExerciseDots items={day.exercises} current={exIdx} sessionData={sessionData} onPick={setExIdx} />

      <div className="scrollable" style={{ padding: '12px 18px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
          {showMuscleMap && <MuscleMap active={[...exDef.primary, ...exDef.secondary]} width={70} />}
          <div style={{ flex: 1 }}>
            <div className="eyebrow">Exercise {exIdx + 1} · {ex.sets}×{ex.reps}</div>
            <div className="display" style={{ fontSize: 28, marginTop: 4, lineHeight: 0.9 }}>{exDef.name}</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 8 }}>
              {exDef.primary.map(m => <span key={m} className="chip solid" style={{ fontSize: 10 }}>{m}</span>)}
            </div>
          </div>
        </div>

        {/* BIG CUE — single, swipeable */}
        <div style={{ background: 'color-mix(in oklch, var(--accent) 14%, var(--surface))', borderRadius: 18, padding: 24, marginBottom: 12, border: '1px solid var(--accent)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
            <span className="eyebrow" style={{ color: 'var(--accent)' }}>Cue {cueIdx + 1} of {exDef.cues.length}</span>
            <div style={{ display: 'flex', gap: 4 }}>
              {exDef.cues.map((_, i) => (
                <div key={i} style={{ width: 5, height: 5, borderRadius: 100, background: i === cueIdx ? 'var(--accent)' : 'var(--fg-3)' }} />
              ))}
            </div>
          </div>
          <div className="display" style={{ fontSize: 36, lineHeight: 1, color: 'var(--fg)', minHeight: 100 }}>
            {exDef.cues[cueIdx]}
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 18 }}>
            <button
              onClick={() => setCueIdx((cueIdx - 1 + exDef.cues.length) % exDef.cues.length)}
              style={{ background: 'transparent', border: '1px solid var(--line)', color: 'var(--fg)', borderRadius: 8, padding: '8px 12px', cursor: 'pointer', fontSize: 12 }}>
              ←
            </button>
            <button
              onClick={() => setCueIdx((cueIdx + 1) % exDef.cues.length)}
              style={{ flex: 1, background: 'var(--accent)', color: 'var(--accent-fg)', border: 'none', borderRadius: 8, padding: '8px 12px', cursor: 'pointer', fontSize: 12, fontWeight: 700 }}>
              Next cue →
            </button>
          </div>
        </div>

        {/* Set rows compact */}
        <div className="eyebrow" style={{ marginBottom: 8 }}>Sets · {exSets.filter(s => s.done).length}/{exSets.length}</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {exSets.map((s, i) => (
            <SetRow key={i} idx={i} set={s} active={i === currentSetIdx} unit={unit}
              onEditWeight={() => setEditTarget({ field: 'weight', setIdx: i })}
              onEditReps={() => setEditTarget({ field: 'reps', setIdx: i })}
              onAdjust={(setIdx, field, delta) => {
                const v = exSets[setIdx][field] + delta;
                updateSet(setIdx, { [field]: Math.max(0, Math.round(v * 100) / 100) });
              }} />
          ))}
          <AddSetButton onClick={addSet} />
        </div>
      </div>

      <CompleteSetCTA isExDone={isExDone} onComplete={completeSet} onNext={goToNextExercise}
        onUndo={undoLastSet} canUndo={exSets.some(s => s.done)}
        currentSet={exSets[currentSetIdx]} unit={unit} setIdx={currentSetIdx} totalSets={exSets.length} />
    </>
  );
}

// =====================================================================
// LAYOUT 4 — COMPACT (everything visible, dense)
// =====================================================================
function LayoutCompact(props) {
  const { day, ex, exDef, exIdx, exSets, currentSetIdx, isExDone,
    completeSet, undoLastSet, addSet, updateSet, goToNextExercise, setEditTarget,
    unit, onExit, setExIdx, totalSetsCompleted, totalSets, showMuscleMap, sessionData } = props;
  return (
    <>
      <WorkoutTopBar ex={ex} exIdx={exIdx} total={day.exercises.length}
        dayName={day.name} onExit={onExit} onSkip={goToNextExercise}
        totalSetsCompleted={totalSetsCompleted} totalSets={totalSets} />
      <ExerciseDots items={day.exercises} current={exIdx} sessionData={sessionData} onPick={setExIdx} />

      <div className="scrollable" style={{ padding: '8px 16px 12px' }}>
        {/* Compact header row: small gif + name + sets prescription */}
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', padding: 10, background: 'var(--surface)', borderRadius: 12, border: '1px solid var(--line)', marginBottom: 10 }}>
          <div className="striped" style={{ width: 64, height: 64, borderRadius: 8, flexShrink: 0 }}>
            <div className="striped-label" style={{ fontSize: 7 }}>gif</div>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="display" style={{ fontSize: 20, lineHeight: 0.95 }}>{exDef.name}</div>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
              {exDef.primary.map(m => <span key={m} className="chip solid" style={{ fontSize: 9, padding: '2px 6px' }}>{m}</span>)}
              {exDef.secondary.slice(0, 1).map(m => <span key={m} className="chip outline" style={{ fontSize: 9, padding: '2px 6px' }}>{m}</span>)}
            </div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div className="display" style={{ fontSize: 22, color: 'var(--accent)' }}>{ex.sets}×{ex.reps}</div>
          </div>
        </div>

        {/* Cues 2x2 grid */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, marginBottom: 10 }}>
          {exDef.cues.map((c, i) => (
            <div key={i} style={{
              padding: 10, borderRadius: 10, background: 'var(--surface-2)',
              display: 'flex', gap: 6, alignItems: 'flex-start',
            }}>
              <div style={{ fontFamily: 'Anton', fontSize: 12, color: 'var(--accent)' }}>{(i + 1).toString().padStart(2, '0')}</div>
              <div style={{ fontSize: 12, color: 'var(--fg)', lineHeight: 1.3 }}>{c}</div>
            </div>
          ))}
        </div>

        {/* Sets grid */}
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, alignItems: 'baseline' }}>
          <div className="eyebrow">Sets</div>
          <span style={{ fontSize: 11, color: 'var(--fg-2)' }}>{exSets.filter(s => s.done).length}/{exSets.length}</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
          {exSets.map((s, i) => (
            <SetRow key={i} idx={i} set={s} active={i === currentSetIdx} unit={unit} compact
              onEditWeight={() => setEditTarget({ field: 'weight', setIdx: i })}
              onEditReps={() => setEditTarget({ field: 'reps', setIdx: i })}
              onAdjust={(setIdx, field, delta) => {
                const v = exSets[setIdx][field] + delta;
                updateSet(setIdx, { [field]: Math.max(0, Math.round(v * 100) / 100) });
              }} />
          ))}
          <AddSetButton onClick={addSet} compact />
        </div>
      </div>

      <CompleteSetCTA isExDone={isExDone} onComplete={completeSet} onNext={goToNextExercise}
        onUndo={undoLastSet} canUndo={exSets.some(s => s.done)}
        currentSet={exSets[currentSetIdx]} unit={unit} setIdx={currentSetIdx} totalSets={exSets.length} />
    </>
  );
}

// ─── Add Set Button ───────────────────────────────────────────────────
function AddSetButton({ onClick, compact }) {
  return (
    <button
      onClick={onClick}
      className="add-set-btn"
      style={{ padding: compact ? '8px 10px' : '10px 12px' }}>
      <Icon.plus width={14} height={14} />
      <span>Add set</span>
    </button>
  );
}

// ─── Set Row ─────────────────────────────────────────────────────────
function SetRow({ idx, set, active, onEditWeight, onEditReps, onAdjust, unit, compact }) {
  const wStep = unit === 'lbs' ? 5 : 2.5;
  const stepBtn = (sign, onClick) => {
    const Comp = sign > 0 ? Icon.plus : Icon.minus;
    return (
      <button
        onClick={(e) => { e.stopPropagation(); onClick(); }}
        className="set-step"
        aria-label={sign > 0 ? 'increase' : 'decrease'}
        disabled={set.done}>
        <Comp width={12} height={12} />
      </button>
    );
  };
  return (
    <div className={`set-row ${active && !set.done ? 'active' : ''} ${set.done ? 'done' : ''}`}
      style={{ padding: compact ? '8px 10px' : '10px 12px', gap: 6 }}>
      <div className="set-num">{(idx + 1).toString().padStart(2, '0')}</div>

      {/* Weight cell with steppers */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
        {stepBtn(-1, () => onAdjust && onAdjust(idx, 'weight', -wStep))}
        <div onClick={onEditWeight} style={{ flex: 1, textAlign: 'center', cursor: 'pointer', minWidth: 0 }}>
          <div className="display" style={{ fontSize: compact ? 18 : 22, color: 'var(--fg)', lineHeight: 1 }}>{formatWeight(set.weight, unit)}</div>
          <div style={{ fontSize: 9, color: 'var(--fg-3)', textTransform: 'uppercase', letterSpacing: '0.1em', marginTop: 2 }}>{unitLabel(unit)}</div>
        </div>
        {stepBtn(+1, () => onAdjust && onAdjust(idx, 'weight', +wStep))}
      </div>

      <div style={{ color: 'var(--fg-3)', fontFamily: 'Anton', fontSize: compact ? 14 : 16 }}>×</div>

      {/* Reps cell with steppers */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
        {stepBtn(-1, () => onAdjust && onAdjust(idx, 'reps', -1))}
        <div onClick={onEditReps} style={{ flex: 1, textAlign: 'center', cursor: 'pointer', minWidth: 0 }}>
          <div className="display" style={{ fontSize: compact ? 18 : 22, color: 'var(--fg)', lineHeight: 1 }}>{set.reps}</div>
          <div style={{ fontSize: 9, color: 'var(--fg-3)', textTransform: 'uppercase', letterSpacing: '0.1em', marginTop: 2 }}>reps</div>
        </div>
        {stepBtn(+1, () => onAdjust && onAdjust(idx, 'reps', +1))}
      </div>

      <div style={{ width: 18, display: 'flex', justifyContent: 'flex-end' }}>
        {set.done && <Icon.check width={16} height={16} style={{ color: 'var(--accent)' }} />}
      </div>
    </div>
  );
}

// ─── Complete-Set CTA ─────────────────────────────────────────────────
function CompleteSetCTA({ isExDone, onComplete, onNext, onUndo, canUndo, currentSet, unit, setIdx, totalSets, compact }) {
  if (isExDone) {
    return (
      <div style={{ padding: '10px 18px 16px', borderTop: '1px solid var(--line)', background: 'var(--bg)' }}>
        {canUndo && (
          <button onClick={onUndo} style={{
            width: '100%', background: 'transparent', border: '1px solid var(--line)', color: 'var(--fg-2)',
            borderRadius: 10, padding: '8px 12px', fontSize: 12, fontWeight: 600, marginBottom: 8, cursor: 'pointer',
          }}>
            Undo last set
          </button>
        )}
        <button onClick={onNext} className="btn-cta-huge" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          Next exercise <Icon.arrow width={20} height={20} style={{ marginLeft: 8 }} />
        </button>
      </div>
    );
  }
  return (
    <div style={{ padding: '10px 18px 16px', borderTop: '1px solid var(--line)', background: 'var(--bg)' }}>
      {canUndo && !compact && (
        <button onClick={onUndo} style={{
          width: '100%', background: 'transparent', border: '1px solid var(--line)', color: 'var(--fg-2)',
          borderRadius: 10, padding: '6px 12px', fontSize: 11, fontWeight: 600, marginBottom: 8, cursor: 'pointer',
        }}>
          Undo last set
        </button>
      )}
      <button onClick={onComplete} className="btn-cta-huge" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 22px' }}>
        <span>Complete Set {setIdx + 1}</span>
        <span style={{ fontSize: 16, opacity: 0.7 }}>{formatWeight(currentSet.weight, unit)}{unitLabel(unit)} × {currentSet.reps}</span>
      </button>
    </div>
  );
}

// ─── Keypad sheet for editing weight/reps ─────────────────────────────
function KeypadSheet({ field, value, unit, onSave, onClose }) {
  const [v, setV] = React.useState(value.toString());
  const press = (k) => {
    if (k === '←') setV(s => s.length > 0 ? s.slice(0, -1) : '');
    else if (k === '.') { if (!v.includes('.')) setV(v + '.'); }
    else setV(v === '0' ? k : v + k);
  };
  const save = () => onSave(parseFloat(v) || 0);
  const label = field === 'weight' ? `Weight (${unitLabel(unit)})` : 'Reps';

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" onClick={e => e.stopPropagation()} style={{ padding: 0 }}>
        <div style={{ padding: '18px 22px 8px' }}>
          <div className="sheet-handle" />
          <div className="eyebrow">{label}</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <div className="huge-num" style={{ fontSize: 80, color: 'var(--accent)' }}>{v || '0'}</div>
            <div style={{ fontSize: 18, color: 'var(--fg-2)', fontWeight: 600 }}>{field === 'weight' ? unitLabel(unit) : 'reps'}</div>
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
            {field === 'weight'
              ? [-2.5, -1.25, +1.25, +2.5, +5].map(d => (
                <button key={d} onClick={() => setV(String(Math.max(0, parseFloat(v || 0) + d)))}
                  style={{ background: 'var(--surface-2)', border: 'none', color: 'var(--fg)', padding: '6px 12px', borderRadius: 100, cursor: 'pointer', fontSize: 12, fontWeight: 600 }}>
                  {d > 0 ? '+' + d : d}
                </button>
              ))
              : [-2, -1, +1, +2, +5].map(d => (
                <button key={d} onClick={() => setV(String(Math.max(0, parseInt(v || 0) + d)))}
                  style={{ background: 'var(--surface-2)', border: 'none', color: 'var(--fg)', padding: '6px 12px', borderRadius: 100, cursor: 'pointer', fontSize: 12, fontWeight: 600 }}>
                  {d > 0 ? '+' + d : d}
                </button>
              ))}
          </div>
        </div>

        <div className="keypad">
          {['7','8','9','4','5','6','1','2','3','.','0','←'].map(k => (
            <button key={k} onClick={() => press(k)}>{k}</button>
          ))}
        </div>
        <div style={{ padding: '0 12px 14px' }}>
          <button onClick={save} className="btn-cta-huge">Save</button>
        </div>
      </div>
    </div>
  );
}

// ─── Workout complete screen ─────────────────────────────────────────
function WorkoutComplete({ dayIndex, onDone }) {
  const day = WORKOUT_DAYS[dayIndex];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', textAlign: 'center', padding: 24, justifyContent: 'space-between' }}>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16 }}>
        <Icon.check width={80} height={80} style={{ color: 'var(--accent)' }} />
        <div className="eyebrow" style={{ color: 'var(--accent)' }}>Day {dayIndex + 1} done</div>
        <div className="display" style={{ fontSize: 56, lineHeight: 0.9 }}>{day.name}<br/>locked in.</div>
        <div style={{ fontSize: 14, color: 'var(--fg-2)', maxWidth: 280 }}>
          {day.exercises.length} exercises · {day.exercises.reduce((s, e) => s + e.sets, 0)} sets · logged.
        </div>
        <div style={{ display: 'flex', gap: 18, marginTop: 20 }}>
          <StatPill label="PRs" value="2" icon="trend" />
          <StatPill label="Vol" value="+8%" icon="trend" />
        </div>
      </div>
      <button onClick={onDone} className="btn-cta-huge">
        Back home →
      </button>
    </div>
  );
}

Object.assign(window, {
  WorkoutSession, WorkoutComplete, WorkoutTopBar, ExerciseDots, RestBar,
  LayoutStacked, LayoutFocus, LayoutCoach, LayoutCompact,
  SetRow, AddSetButton, CompleteSetCTA, KeypadSheet,
});
