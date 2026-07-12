// settings.jsx — Settings screen: customize workout, units, account

function SettingsScreen({ unit, theme, onEditRoutine, onResetOnboarding, splitName }) {
  return (
    <div className="scrollable" style={{ padding: '0 0 20px' }}>
      <div style={{ padding: '12px 22px 8px' }}>
        <div className="eyebrow">Settings</div>
        <div className="display" style={{ fontSize: 36, marginTop: 6 }}>Settings</div>
      </div>

      <SettingsGroup title="Routine">
        <SettingsRow icon="dumbbell" label="Your routine" sub={splitName + ' · 5 days'} onClick={onEditRoutine} />
        <SettingsRow icon="swap" label="Switch split" sub="Change to push/pull/legs, etc" onClick={onResetOnboarding} />
        <SettingsRow icon="edit" label="Edit exercises" sub="Add, remove, reorder" />
        <SettingsRow icon="timer" label="Default rest" sub="90 seconds" />
      </SettingsGroup>

      <SettingsGroup title="Workout experience">
        <SettingsRow icon="chart" label="Layout style" sub="Stacked · open Tweaks panel" />
        <SettingsRow icon="bell" label="Notifications" sub="Daily reminders · 6:30 PM" toggle defaultOn />
        <SettingsRow icon="flame" label="Auto-start rest timer" sub="After each set" toggle defaultOn />
        <SettingsRow icon="check" label="Haptics on set complete" toggle defaultOn />
      </SettingsGroup>

      <SettingsGroup title="Preferences">
        <SettingsRow icon="trend" label="Units" sub={unitLabel(unit) === 'kg' ? 'Kilograms (kg)' : 'Pounds (lbs)'} />
        <SettingsRow icon="cog" label="Theme" sub={theme === 'dark' ? 'Dark' : 'Light'} />
        <SettingsRow icon="user" label="Profile & body metrics" />
      </SettingsGroup>

      <SettingsGroup title="Account">
        <SettingsRow icon="user" label="Sync to cloud" toggle defaultOn />
        <SettingsRow icon="more" label="Export workout history" sub="CSV / JSON" />
        <SettingsRow icon="close" label="Delete all data" danger />
      </SettingsGroup>

      <div style={{ textAlign: 'center', padding: '22px 22px 0', color: 'var(--fg-3)', fontSize: 11 }}>
        Get Gym Done · v1.0.0 (beta)
      </div>
    </div>
  );
}

function SettingsGroup({ title, children }) {
  return (
    <div style={{ padding: '14px 22px 4px' }}>
      <div className="eyebrow" style={{ marginBottom: 8 }}>{title}</div>
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        {React.Children.map(children, (c, i) => (
          <React.Fragment>
            {i > 0 && <div style={{ height: 1, background: 'var(--line)', marginLeft: 52 }} />}
            {c}
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}

function SettingsRow({ icon, label, sub, toggle, defaultOn, onClick, danger }) {
  const [on, setOn] = React.useState(!!defaultOn);
  const IconComp = Icon[icon] || Icon.cog;
  return (
    <div
      onClick={() => { if (toggle) setOn(!on); else if (onClick) onClick(); }}
      className="row-tap"
      style={{
        display: 'flex', alignItems: 'center', gap: 14,
        padding: '14px 16px', cursor: 'pointer',
      }}>
      <div style={{
        width: 32, height: 32, borderRadius: 8,
        background: danger ? 'color-mix(in oklch, var(--danger) 18%, var(--surface))' : 'var(--surface-2)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <IconComp width={16} height={16} style={{ color: danger ? 'var(--danger)' : 'var(--fg)' }} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 500, color: danger ? 'var(--danger)' : 'var(--fg)' }}>{label}</div>
        {sub && <div style={{ fontSize: 11, color: 'var(--fg-2)', marginTop: 2 }}>{sub}</div>}
      </div>
      {toggle ? (
        <div style={{
          width: 38, height: 22, borderRadius: 100,
          background: on ? 'var(--accent)' : 'var(--surface-3)',
          position: 'relative', transition: 'background 200ms ease',
        }}>
          <div style={{
            position: 'absolute', top: 2, left: on ? 18 : 2,
            width: 18, height: 18, borderRadius: 100,
            background: on ? 'var(--accent-fg)' : 'var(--fg)',
            transition: 'left 200ms ease',
          }} />
        </div>
      ) : (
        <Icon.chev width={16} height={16} style={{ color: 'var(--fg-3)' }} />
      )}
    </div>
  );
}

Object.assign(window, { SettingsScreen, SettingsGroup, SettingsRow });
