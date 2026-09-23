import { Capacitor, registerPlugin } from '@capacitor/core';

export const isNative = Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';

const NativeTrigger = registerPlugin('EmergencyTrigger');

export const fileSrc = (path) => (isNative ? Capacitor.convertFileSrc(path) : path);

/* ---------- Browser preview: a stand-in so the UI can be checked without a phone ---------- */

function createMock() {
  const listeners = new Set();
  const state = {
    accessibilityEnabled: false,
    triggerConnected: false,
    microphone: false,
    notifications: false,
    recording: false,
    recordingStartedAt: 0,
    lastError: null,
    settings: {
      enabled: true, mode: 'presses', pressCount: 3, windowMs: 1500,
      holdMs: 2000, vibrate: true, patternStops: false, maxMinutes: 60,
    },
  };
  const recordings = [
    { name: 'rec_20260921_223104.m4a', path: '', size: 1843200, modified: Date.now() - 2 * 864e5, durationMs: 232000, inProgress: false },
    { name: 'rec_20260918_081547.m4a', path: '', size: 402000, modified: Date.now() - 5 * 864e5, durationMs: 49000, inProgress: false },
  ];
  const snapshot = () => JSON.parse(JSON.stringify(state));
  const emit = () => listeners.forEach((fn) => fn(snapshot()));

  return {
    async getStatus() { return snapshot(); },
    async requestAppPermissions() { state.microphone = true; state.notifications = true; emit(); return snapshot(); },
    async openAccessibilitySettings() { state.accessibilityEnabled = true; state.triggerConnected = true; emit(); },
    async openAppSettings() {},
    async setSettings(s) { Object.assign(state.settings, s); emit(); return snapshot(); },
    async startRecording() {
      state.recording = true; state.recordingStartedAt = Date.now();
      recordings.unshift({ name: 'rec_preview.m4a', path: '', size: 0, modified: Date.now(), durationMs: 0, inProgress: true });
      emit();
    },
    async stopRecording() {
      const r = recordings.find((x) => x.inProgress);
      if (r) { r.inProgress = false; r.durationMs = Date.now() - state.recordingStartedAt; r.size = 64000; }
      state.recording = false; state.recordingStartedAt = 0; emit();
    },
    async clearError() { state.lastError = null; return snapshot(); },
    async listRecordings() { return { recordings: recordings.map((r) => ({ ...r })) }; },
    async deleteRecording({ name }) { const i = recordings.findIndex((r) => r.name === name); if (i >= 0) recordings.splice(i, 1); },
    async shareRecording() {},
    async addListener(_evt, fn) { listeners.add(fn); return { remove: async () => listeners.delete(fn) }; },
  };
}

export const Trigger = isNative ? NativeTrigger : createMock();
