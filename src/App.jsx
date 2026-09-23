import React, { useCallback, useEffect, useState } from 'react';
import { Trigger, isNative, fileSrc } from './native.js';

const fmtClock = (ms) => {
  const t = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(t / 3600);
  const m = Math.floor((t % 3600) / 60);
  const s = t % 60;
  const mm = String(m).padStart(h ? 2 : 1, '0');
  return `${h ? h + ':' : ''}${mm}:${String(s).padStart(2, '0')}`;
};
const fmtSize = (b) => (b > 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);
const fmtDate = (ts) =>
  new Date(ts).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });

function useNow(active) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return undefined;
    const id = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(id);
  }, [active]);
  return now;
}

export default function App() {
  const [status, setStatus] = useState(null);
  const [recordings, setRecordings] = useState([]);
  const [playing, setPlaying] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);
  const [notice, setNotice] = useState(null);

  const refreshList = useCallback(async () => {
    try {
      const { recordings: list } = await Trigger.listRecordings();
      setRecordings(list);
    } catch (e) {
      setNotice(e.message || String(e));
    }
  }, []);

  useEffect(() => {
    let handle;
    (async () => {
      setStatus(await Trigger.getStatus());
      refreshList();
      handle = await Trigger.addListener('stateChanged', (s) => {
        setStatus(s);
        refreshList();
      });
    })();
    return () => { handle && handle.remove(); };
  }, [refreshList]);

  const now = useNow(status?.recording);

  if (!status) return <div className="app loading" aria-busy="true" />;

  const s = status.settings;
  const permsDone = status.microphone && status.notifications;
  const triggerOn = status.accessibilityEnabled;
  const ready = permsDone && triggerOn && s.enabled;

  const state = status.recording ? 'recording' : ready ? 'ready' : 'setup';
  const pattern = s.mode === 'hold'
    ? `hold volume down for ${s.holdMs / 1000} seconds`
    : `press volume down ${s.pressCount} times quickly`;

  const run = async (fn) => {
    try {
      const res = await fn();
      if (res && res.settings) setStatus(res);
    } catch (e) {
      setNotice(e.message || String(e));
    }
  };

  const update = (patch) => run(() => Trigger.setSettings(patch));

  return (
    <div className="app">
      {!isNative && (
        <p className="preview">Browser preview. Buttons are simulated; install the Android build to record.</p>
      )}

      <section className={`state state-${state}`} aria-live="polite">
        <div className="signal" aria-hidden="true"><span /></div>
        <h1 className="state-word">
          {state === 'recording' ? 'Recording' : state === 'ready' ? 'Ready' : 'Not set up'}
        </h1>
        {state === 'recording' && (
          <p className="state-line clock">{fmtClock(now - status.recordingStartedAt)}</p>
        )}
        {state === 'ready' && <p className="state-line">To start recording, {pattern}.</p>}
        {state === 'setup' && <p className="state-line">Finish the steps below and the volume button will start a recording.</p>}

        {status.recording ? (
          <button className="primary stop" onClick={() => run(() => Trigger.stopRecording())}>Stop recording</button>
        ) : (
          <button
            className="primary"
            disabled={!status.microphone}
            onClick={() => run(() => Trigger.startRecording())}
          >
            Start recording now
          </button>
        )}
      </section>

      {(status.lastError || notice) && (
        <div className="alert" role="alert">
          <p>{status.lastError || notice}</p>
          <button className="text" onClick={() => { setNotice(null); run(() => Trigger.clearError()); }}>Dismiss</button>
        </div>
      )}

      <section className="block">
        <h2>Setup</h2>
        <ol className="steps">
          <li className={permsDone ? 'done' : ''}>
            <div>
              <h3>Allow microphone and notifications</h3>
              <p>Needed to record, and to keep recording with the screen locked.</p>
            </div>
            {permsDone ? <span className="tick">Done</span> : (
              <button className="secondary" onClick={() => run(() => Trigger.requestAppPermissions())}>Allow</button>
            )}
          </li>
          <li className={triggerOn ? 'done' : ''}>
            <div>
              <h3>Turn on the volume trigger</h3>
              <p>In Accessibility, open Installed apps (or Downloaded apps), choose Quietline emergency trigger and switch it on.</p>
            </div>
            {triggerOn ? <span className="tick">Done</span> : (
              <button className="secondary" onClick={() => run(() => Trigger.openAccessibilitySettings())}>Open settings</button>
            )}
          </li>
          <li className={recordings.length > 0 ? 'done' : ''}>
            <div>
              <h3>Test it once</h3>
              <p>Lock your phone with the screen still on, then {pattern}. You'll feel one buzz when it starts and two when it stops.</p>
            </div>
            {recordings.length > 0 && <span className="tick">Done</span>}
          </li>
        </ol>
      </section>

      <section className="block">
        <h2>Trigger</h2>

        <label className="row">
          <span>Volume trigger on</span>
          <input type="checkbox" className="switch" checked={s.enabled} onChange={(e) => update({ enabled: e.target.checked })} />
        </label>

        <div className="row stacked">
          <span id="mode-label">Pattern</span>
          <div className="segmented" role="radiogroup" aria-labelledby="mode-label">
            <button role="radio" aria-checked={s.mode === 'presses'} onClick={() => update({ mode: 'presses' })}>Quick presses</button>
            <button role="radio" aria-checked={s.mode === 'hold'} onClick={() => update({ mode: 'hold' })}>Press and hold</button>
          </div>
        </div>

        {s.mode === 'presses' ? (
          <div className="row">
            <span>Number of presses</span>
            <Stepper value={s.pressCount} min={2} max={6} onChange={(v) => update({ pressCount: v })} />
          </div>
        ) : (
          <div className="row">
            <span>Hold for</span>
            <Stepper value={s.holdMs / 1000} min={1} max={6} suffix=" s" onChange={(v) => update({ holdMs: v * 1000 })} />
          </div>
        )}

        <div className="row">
          <span>Stop automatically after</span>
          <Stepper
            value={s.maxMinutes}
            min={15} max={240} step={15} suffix=" min"
            onChange={(v) => update({ maxMinutes: v })}
          />
        </div>

        <label className="row">
          <span>Buzz when recording starts and stops</span>
          <input type="checkbox" className="switch" checked={s.vibrate} onChange={(e) => update({ vibrate: e.target.checked })} />
        </label>

        <label className="row">
          <span>
            Same pattern stops recording
            <small>Off is safer: a panicked extra press won't end the recording.</small>
          </span>
          <input type="checkbox" className="switch" checked={s.patternStops} onChange={(e) => update({ patternStops: e.target.checked })} />
        </label>
      </section>

      <section className="block">
        <h2>Recordings</h2>
        {recordings.length === 0 ? (
          <p className="empty">No recordings yet. Run the test above to make your first one.</p>
        ) : (
          <ul className="recs">
            {recordings.map((r) => (
              <li key={r.name}>
                <div className="rec-meta">
                  <strong>{fmtDate(r.modified)}</strong>
                  <span>{r.inProgress ? 'Recording now' : `${fmtClock(r.durationMs)}, ${fmtSize(r.size)}`}</span>
                </div>
                {!r.inProgress && (
                  <div className="rec-actions">
                    <button className="text" onClick={() => setPlaying(playing === r.name ? null : r.name)}>
                      {playing === r.name ? 'Close' : 'Play'}
                    </button>
                    <button className="text" onClick={() => run(() => Trigger.shareRecording({ name: r.name }))}>Share</button>
                    {confirmDelete === r.name ? (
                      <button
                        className="text danger"
                        onClick={async () => {
                          setConfirmDelete(null);
                          await run(() => Trigger.deleteRecording({ name: r.name }));
                          refreshList();
                        }}
                      >
                        Confirm delete
                      </button>
                    ) : (
                      <button className="text" onClick={() => setConfirmDelete(r.name)}>Delete</button>
                    )}
                  </div>
                )}
                {playing === r.name && r.path && (
                  <audio controls autoPlay src={fileSrc(r.path)} className="player" />
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <p className="footnote">
        Recordings stay inside this app on your phone. Share one to save it elsewhere or send it to someone you trust.
      </p>
    </div>
  );
}

function Stepper({ value, min, max, step = 1, suffix = '', onChange }) {
  return (
    <div className="stepper">
      <button aria-label="Decrease" disabled={value <= min} onClick={() => onChange(Math.max(min, value - step))}>−</button>
      <output>{value}{suffix}</output>
      <button aria-label="Increase" disabled={value >= max} onClick={() => onChange(Math.min(max, value + step))}>+</button>
    </div>
  );
}
