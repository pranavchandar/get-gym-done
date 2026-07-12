// profile.jsx — Metrics: weight progression per exercise, bodyweight, streak

function ProfileScreen({ unit }) {
  const [exId, setExId] = React.useState('incline-smith-press');
  const exHistory = EXERCISE_HISTORY[exId] || [];
  const chartData = exHistory.map(p => ({ x: p.date, y: formatWeight(p.weight, unit) }));
  const bwData = BODYWEIGHT.map(p => ({ x: p.date, y: parseFloat(formatWeight(p.kg, unit).toFixed(1)) }));

  const latest = exHistory[exHistory.length - 1];
  const earliest = exHistory[0];
  const change = latest && earliest ? latest.weight - earliest.weight : 0;
  const changePct = earliest && earliest.weight ? (change / earliest.weight * 100).toFixed(0) : 0;

  const bwLatest = BODYWEIGHT[BODYWEIGHT.length - 1];
  const bwEarliest = BODYWEIGHT[0];
  const bwChange = bwLatest.kg - bwEarliest.kg;

  // build streak heatmap for last 8 weeks (7x8)
  const today = parseLocalISO(TODAY_ISO);
  const heatCells = [];
  for (let w = 7; w >= 0; w--) {
    for (let d = 0; d < 7; d++) {
      const dt = new Date(today);
      const offset = w * 7 + (today.getDay() - d);
      dt.setDate(dt.getDate() - offset);
      const iso = toLocalISO(dt);
      const has = WORKOUT_LOG.find(l => l.date === iso);
      heatCells.push({ iso, has, date: dt });
    }
  }
  const weekStreak = computeWeekStreak(WORKOUT_LOG, today);
  const avgPerWeek = (WORKOUT_LOG.length / 8).toFixed(1);

  return (
    <div className="scrollable" style={{ padding: '0 0 20px' }}>
      {/* Profile header */}
      <div style={{ padding: '12px 22px 18px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={{
            width: 64, height: 64, borderRadius: 100,
            background: 'var(--accent)', color: 'var(--accent-fg)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'Anton', fontSize: 32,
          }}>
            AX
          </div>
          <div style={{ flex: 1 }}>
            <div className="display" style={{ fontSize: 24, lineHeight: 1 }}>Alex Mercer</div>
            <div style={{ fontSize: 12, color: 'var(--fg-2)', marginTop: 4 }}>5'10" · 26 sessions · 8 weeks in</div>
          </div>
          <IconBtn icon="edit" />
        </div>
      </div>

      {/* PR summary */}
      <div style={{ padding: '0 22px 18px' }}>
        <div style={{ display: 'flex', gap: 8 }}>
          <BigStat label="Total volume" value="48k" unit={unitLabel(unit)} trend="+12%" />
          <BigStat label="PRs this month" value="6" trend="hot" />
        </div>
      </div>

      {/* Exercise progression chart */}
      <div style={{ padding: '0 22px 18px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 6 }}>
          <div className="eyebrow">Exercise progression</div>
          <span style={{ fontSize: 11, color: 'var(--fg-3)' }}>tap to compare ↓</span>
        </div>

        <div className="card" style={{ padding: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--fg)', marginBottom: 10 }}>{EXERCISES[exId].name}</div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 4 }}>
            <div>
              <div className="huge-num" style={{ fontSize: 56, color: 'var(--fg)' }}>
                {latest ? formatWeight(latest.weight, unit) : '—'}
                <span style={{ fontSize: 18, color: 'var(--fg-2)', marginLeft: 4, fontWeight: 400 }}>{unitLabel(unit)}</span>
              </div>
              <div style={{ fontSize: 11, color: 'var(--fg-2)' }}>Current PR</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--accent)' }}>
                <Icon.trend width={14} height={14} />
                <span style={{ fontFamily: 'Anton', fontSize: 18 }}>+{formatWeight(change, unit)}{unitLabel(unit)}</span>
              </div>
              <div style={{ fontSize: 11, color: 'var(--fg-2)' }}>+{changePct}% in 8w</div>
            </div>
          </div>
          <div style={{ margin: '8px -4px 0' }}>
            <LineChart data={chartData} height={140} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: 'var(--fg-3)', marginTop: 4 }}>
            {chartData[0] && <span>{formatDate(chartData[0].x)}</span>}
            <span>Today</span>
          </div>
        </div>

        {/* Other exercise chips - pick to change chart */}
        <div style={{ display: 'flex', gap: 6, overflowX: 'auto', marginTop: 10, paddingBottom: 4 }}>
          {Object.keys(EXERCISE_HISTORY).map(id => (
            <div
              key={id}
              onClick={() => setExId(id)}
              className="chip"
              style={{
                background: exId === id ? 'var(--accent)' : 'var(--surface-2)',
                color: exId === id ? 'var(--accent-fg)' : 'var(--fg-2)',
                cursor: 'pointer', flexShrink: 0, fontSize: 11,
              }}>
              {EXERCISES[id].name}
            </div>
          ))}
        </div>
      </div>

      {/* Bodyweight */}
      <div style={{ padding: '0 22px 18px' }}>
        <div className="eyebrow" style={{ marginBottom: 6 }}>Bodyweight</div>
        <div className="card" style={{ padding: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 4 }}>
            <div>
              <div className="huge-num" style={{ fontSize: 48, color: 'var(--fg)' }}>
                {formatWeight(bwLatest.kg, unit).toFixed(1)}
                <span style={{ fontSize: 16, color: 'var(--fg-2)', marginLeft: 4, fontWeight: 400 }}>{unitLabel(unit)}</span>
              </div>
              <div style={{ fontSize: 11, color: 'var(--fg-2)' }}>Today</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--accent)' }}>
                <Icon.trend width={14} height={14} />
                <span style={{ fontFamily: 'Anton', fontSize: 16 }}>+{formatWeight(bwChange, unit).toFixed(1)}{unitLabel(unit)}</span>
              </div>
              <div style={{ fontSize: 11, color: 'var(--fg-2)' }}>8 weeks</div>
            </div>
          </div>
          <div style={{ margin: '8px -4px 0' }}>
            <LineChart data={bwData} height={100} color="oklch(0.70 0.22 25)" />
          </div>
        </div>
      </div>

      {/* Consistency heatmap */}
      <div style={{ padding: '0 22px 18px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
          <div className="eyebrow">Consistency</div>
          <span style={{ fontSize: 11, color: 'var(--fg-2)' }}>Last 8 weeks</span>
        </div>
        <div className="card" style={{ padding: 16 }}>
          <div style={{ display: 'flex', gap: 4 }}>
            {['S','M','T','W','T','F','S'].map((d, i) => (
              <div key={i} style={{ flex: 1, textAlign: 'center', fontSize: 9, color: 'var(--fg-3)', letterSpacing: '0.08em' }}>{d}</div>
            ))}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4, marginTop: 6 }}>
            {heatCells.map((c, i) => {
              const isToday = c.iso === TODAY_ISO;
              return (
                <div key={i} style={{
                  aspectRatio: 1,
                  borderRadius: 4,
                  background: c.has ? 'var(--accent)' : 'var(--surface-2)',
                  outline: isToday ? '1.5px solid var(--accent)' : 'none',
                  outlineOffset: -1,
                }} />
              );
            })}
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12, fontSize: 11, color: 'var(--fg-2)' }}>
            <span><strong style={{ color: 'var(--fg)' }}>{WORKOUT_LOG.length}</strong> sessions</span>
            <span><strong style={{ color: 'var(--fg)' }}>{avgPerWeek}</strong> avg / week</span>
            <span><strong style={{ color: 'var(--fg)' }}>{weekStreak}</strong> week streak</span>
          </div>
        </div>
      </div>

      {/* Recent PRs */}
      <div style={{ padding: '0 22px 8px' }}>
        <div className="eyebrow" style={{ marginBottom: 8 }}>Recent personal records</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {[
            { ex: 'Incline Smith Press', w: 72.5, date: '1d ago' },
            { ex: 'Wide Grip Pulldown', w: 65, date: '1d ago' },
            { ex: 'Barbell Squat', w: 120, date: '6d ago' },
            { ex: 'Deadlift', w: 140, date: '10d ago' },
          ].map((pr, i) => (
            <div key={i} className="card-flat" style={{ padding: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{ width: 36, height: 36, borderRadius: 8, background: 'color-mix(in oklch, var(--accent) 22%, var(--surface))', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon.trend width={18} height={18} style={{ color: 'var(--accent)' }} />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 13, fontWeight: 600 }}>{pr.ex}</div>
                <div style={{ fontSize: 11, color: 'var(--fg-2)' }}>{pr.date}</div>
              </div>
              <div className="display" style={{ fontSize: 22, color: 'var(--accent)' }}>
                {formatWeight(pr.w, unit)}<span style={{ fontSize: 12, color: 'var(--fg-2)', fontWeight: 400, marginLeft: 2 }}>{unitLabel(unit)}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function BigStat({ label, value, unit, trend }) {
  return (
    <div className="card" style={{ flex: 1, padding: 14 }}>
      <div style={{ fontSize: 10, color: 'var(--fg-2)', textTransform: 'uppercase', letterSpacing: '0.1em' }}>{label}</div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginTop: 8 }}>
        <div className="display" style={{ fontSize: 36, color: 'var(--fg)' }}>{value}</div>
        {unit && <div style={{ fontSize: 12, color: 'var(--fg-2)' }}>{unit}</div>}
      </div>
      {trend === 'hot' ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--accent)', marginTop: 4 }}>
          <Icon.flame width={12} height={12} />
          <span style={{ fontSize: 11, fontWeight: 600 }}>On fire</span>
        </div>
      ) : trend ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--accent)', marginTop: 4 }}>
          <Icon.trend width={12} height={12} />
          <span style={{ fontSize: 11, fontWeight: 600 }}>{trend}</span>
        </div>
      ) : null}
    </div>
  );
}

Object.assign(window, { ProfileScreen, BigStat });
