import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';

const source = readFileSync(new URL('../src/app/utils/resolverInput.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const api = {};
new Function('require', 'exports', compiled)(createRequire(import.meta.url), api);
const key = (value, modifiers = {}) => ({ key: value, ctrlKey: false, metaKey: false, altKey: false, shiftKey: false, ...modifiers });
for (const value of [' ', 'f', 'F', 'PageDown']) assert.equal(api.resolverAction(key(value)), 'forward');
for (const value of ['r', 'R', 'b', 'B', 'PageUp']) assert.equal(api.resolverAction(key(value)), 'reverse');
for (const value of ['ArrowLeft', 'ArrowRight', 'Enter', 'Backspace', 'Tab', 'Home', 'End', 'Escape', 'F5', 'F11', 'F12', 'q']) assert.equal(api.resolverAction(key(value)), null);
for (const value of ['+', '-', '=', '0', 'l', 'r', 'p', 'w']) {
  assert.equal(api.resolverAction(key(value, { ctrlKey: true })), null);
  assert.equal(api.resolverAction(key(value, { metaKey: true })), null);
}
assert.equal(api.resolverAction(key('q', { ctrlKey: true })), 'quit');
assert.equal(api.resolverAction(key('Q', { shiftKey: true })), 'quit');
assert.equal(api.resolverAction(key('Escape', { shiftKey: true })), 'quit');
assert.equal(api.resolverAction(key('ArrowUp')), 'faster');
assert.equal(api.resolverAction(key('ArrowDown')), 'slower');
assert.equal(api.resolverAction(key('[')), 'scrollSlower');
assert.equal(api.resolverAction(key(']')), 'scrollFaster');

class Target {
  listeners = new Map();
  addEventListener(type, callback) { const list = this.listeners.get(type) || []; list.push(callback); this.listeners.set(type, list); }
  removeEventListener(type, callback) { this.listeners.set(type, (this.listeners.get(type) || []).filter(item => item !== callback)); }
  fire(type, extra = {}) {
    const event = { cancelable: true, defaultPrevented: false, preventedPropagation: false, preventDefault() { this.defaultPrevented = true; }, stopImmediatePropagation() { this.preventedPropagation = true; }, ...extra };
    for (const listener of this.listeners.get(type) || []) listener(event);
    return event;
  }
}
const classes = () => { const values = new Set(); return { add: x => values.add(x), remove: x => values.delete(x), contains: x => values.has(x) }; };
const original = Object.fromEntries(['window', 'document', 'navigator'].map(name => [name, Object.getOwnPropertyDescriptor(globalThis, name)]));
const fakeWindow = new Target();
const fakeDocument = Object.assign(new Target(), { fullscreenElement: null, documentElement: { classList: classes() }, body: { classList: classes() } });
const actions = [];
try {
  for (const [name, value] of Object.entries({ window: fakeWindow, document: fakeDocument, navigator: {} })) Object.defineProperty(globalThis, name, { value, configurable: true });
  let focused = false;
  const cleanup = api.installResolverInput(action => actions.push(action), { focus: () => { focused = true; } });
  assert.ok(focused);
  assert.ok(fakeDocument.body.classList.contains('resolver-input-active'));
  for (const type of ['wheel', 'touchmove', 'gesturestart', 'gesturechange', 'contextmenu', 'mousedown', 'dragstart', 'selectstart']) {
    const event = fakeWindow.fire(type);
    assert.ok(event.defaultPrevented, type);
    assert.ok(event.preventedPropagation, type);
  }
  for (const value of ['Tab', 'ArrowLeft', 'Enter']) assert.ok(fakeWindow.fire('keydown', key(value)).defaultPrevented);
  assert.ok(fakeWindow.fire('keydown', key('+', { ctrlKey: true })).defaultPrevented);
  assert.deepEqual(actions, []);
  fakeWindow.fire('keydown', key('f'));
  assert.deepEqual(actions, ['forward']);
  fakeWindow.fire('keydown', key('f', { repeat: true }));
  assert.deepEqual(actions, ['forward']);
  cleanup();
  assert.ok(!fakeDocument.body.classList.contains('resolver-input-active'));
  assert.equal(fakeWindow.fire('wheel').defaultPrevented, false);
  assert.equal(fakeWindow.fire('keydown', key('f')).defaultPrevented, false);

  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  let busy = true;
  let forwards = 0;
  const heldActions = [];
  const stopHoldTest = api.installResolverInput(action => {
    assert.equal(action, 'forward');
    heldActions.push(action);
    forwards++;
    busy = true;
  }, { focus() {} }, () => busy);
  fakeWindow.fire('keydown', key(' '));
  fakeWindow.fire('keydown', key(' ', { repeat: true }));
  await sleep(60);
  assert.equal(forwards, 0, 'a held space must wait for an existing busy step');
  busy = false;
  await sleep(100);
  assert.equal(forwards, 1, 'held space resumes after busy even without another keydown');
  await sleep(450);
  assert.equal(forwards, 1, 'held space must not overlap a busy step');
  busy = false;
  await sleep(100);
  assert.equal(forwards, 2, 'held space advances again after the next busy step');
  assert.ok(heldActions.every(action => action === 'forward'), 'holding only repeats normal advance');
  fakeWindow.fire('keyup', key(' '));
  busy = false;
  await sleep(450);
  assert.equal(forwards, 2, 'release must cancel future steps');
  fakeWindow.fire('keydown', key(' '));
  fakeWindow.fire('blur');
  busy = false;
  const beforeBlur = forwards;
  await sleep(450);
  assert.equal(forwards, beforeBlur, 'losing focus must stop a held key');
  fakeWindow.fire('keydown', key(' '));
  fakeDocument.hidden = true;
  fakeDocument.fire('visibilitychange');
  busy = false;
  const beforeHidden = forwards;
  await sleep(450);
  assert.equal(forwards, beforeHidden, 'hiding the page must stop a held key');
  fakeDocument.hidden = false;
  fakeWindow.fire('keydown', key(' '));
  stopHoldTest();
  busy = false;
  const beforeCleanup = forwards;
  await sleep(450);
  assert.equal(forwards, beforeCleanup, 'cleanup must cancel a held key');
  assert.ok(!fakeDocument.body.classList.contains('resolver-input-active'));
} finally {
  for (const [name, descriptor] of Object.entries(original)) { if (descriptor) Object.defineProperty(globalThis, name, descriptor); else delete globalThis[name]; }
}
console.log('Resolver native bindings, input suppression, and cleanup regression checks passed.');
