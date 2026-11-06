/** Resolver 2.7.1354 ResolverUI key bindings, not browser navigation bindings. */
export type ResolverAction = 'forward' | 'reverse' | 'fastForward' | 'fastReverse' | 'reset'
  | 'faster' | 'slower' | 'scrollFaster' | 'scrollSlower' | 'resetSpeed' | 'pauseScroll' | 'info' | 'screen' | 'quit';
export type ResolverKey = Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'shiftKey' | 'metaKey' | 'altKey'>;
export function resolverAction(event: ResolverKey): ResolverAction | null {
  const key = event.key;
  if (!event.metaKey && !event.altKey && (event.ctrlKey || event.shiftKey) && (key.toLowerCase() === 'q' || key === 'Escape')) return 'quit';
  if (event.ctrlKey || event.metaKey || event.altKey) return null;
  switch (key) {
    case ' ': case 'f': case 'F': case 'PageDown': return 'forward';
    case 'r': case 'R': case 'b': case 'B': case 'PageUp': return 'reverse';
    case '1': return 'fastReverse';
    case '2': return 'fastForward';
    case '0': return 'reset';
    case '+': case '=': case 'ArrowUp': return 'faster';
    case '-': case '_': case 'ArrowDown': return 'slower';
    case '[': case '{': return 'scrollSlower';
    case ']': case '}': return 'scrollFaster';
    case 'j': case 'J': return 'resetSpeed';
    case 'p': case 'P': return 'pauseScroll';
    case 'i': case 'I': return 'info';
    case 's': case 'S': return 'screen';
    default: return null;
  }
}

type KeyboardLock = { lock?: () => Promise<void>; unlock?: () => void };
let keyboardOwner: symbol | null = null;

/** Scope native-input suppression to the authorized resolver, restoring normal page behavior on exit. */
export function installResolverInput(onAction: (action: ResolverAction) => void, root: HTMLElement, isBusy: () => boolean = () => false, getStepSpeed: () => number = () => 1) {
  let active = true;
  let spaceHeld = false;
  let spaceTimer: ReturnType<typeof setTimeout> | undefined;
  const owner = Symbol('resolver');
  keyboardOwner = owner;
  let lastAction = -Infinity;
  let lastSpeedAction = -Infinity;
  const intervalMs = (duration: number) => duration / Math.min(4, Math.max(.25, getStepSpeed() || 1));
  const block = (event: Event) => { if (event.cancelable) event.preventDefault(); event.stopImmediatePropagation(); };
  const dispatch = (action: ResolverAction) => {
    const now = performance.now();
    const motion = ['forward', 'fastForward', 'reverse', 'fastReverse', 'reset'].includes(action);
    const interval = intervalMs(action === 'forward' || action === 'fastForward' ? 400 : 200);
    if (motion && now - lastAction < interval) return;
    if (motion) lastAction = now;
    if (['faster', 'slower', 'scrollFaster', 'scrollSlower'].includes(action)) {
      if (now - lastSpeedAction < 100) return;
      lastSpeedAction = now;
    }
    onAction(action);
  };
  const stopSpace = () => {
    spaceHeld = false;
    if (spaceTimer !== undefined) clearTimeout(spaceTimer);
    spaceTimer = undefined;
  };
  const continueSpace = () => {
    spaceTimer = undefined;
    if (!active || !spaceHeld) return;
    const remaining = intervalMs(400) - (performance.now() - lastAction);
    if (remaining <= 0 && !isBusy()) dispatch('forward');
    if (active && spaceHeld) spaceTimer = setTimeout(continueSpace, Math.max(1, Math.min(40, remaining > 0 ? remaining : 400)));
  };
  const keydown = (event: KeyboardEvent) => {
    block(event);
    const action = resolverAction(event);
    if (event.key === ' ' && action === 'forward') {
      if (!spaceHeld) {
        spaceHeld = true;
        if (!isBusy()) dispatch('forward');
        continueSpace();
      }
      return;
    }
    if (action && (!event.repeat || ['faster', 'slower', 'scrollFaster', 'scrollSlower'].includes(action))) {
      if (['reverse', 'fastReverse', 'reset', 'quit'].includes(action)) stopSpace();
      dispatch(action);
    }
  };
  const keyup = (event: KeyboardEvent) => { block(event); if (event.key === ' ') stopSpace(); };
  const onBlur = () => stopSpace();
  const onVisibilityChange = () => { if (document.hidden) stopSpace(); };
  const click = (event: MouseEvent) => { block(event); dispatch('forward'); };
  const keyboard = (navigator as Navigator & { keyboard?: KeyboardLock }).keyboard;
  const lockKeyboard = () => {
    if (!active || !document.fullscreenElement || !keyboard?.lock) return;
    // Browser/OS escape hatches remain under the browser's control.
    void keyboard.lock().then(() => { if (!active && keyboardOwner === null) keyboard.unlock?.(); }).catch(() => {});
  };
  document.documentElement.classList.add('resolver-input-active');
  document.body.classList.add('resolver-input-active');
  root.focus({ preventScroll: true });
  const blocks = ['wheel', 'touchmove', 'gesturestart', 'gesturechange', 'gestureend', 'contextmenu', 'dragstart', 'selectstart', 'mousedown'];
  const options = { capture: true, passive: false };
  for (const name of blocks) window.addEventListener(name, block, options);
  window.addEventListener('keydown', keydown, options);
  window.addEventListener('keypress', block, options);
  window.addEventListener('keyup', keyup, options);
  window.addEventListener('blur', onBlur);
  window.addEventListener('click', click, options);
  window.addEventListener('auxclick', click, options);
  document.addEventListener('fullscreenchange', lockKeyboard);
  document.addEventListener('visibilitychange', onVisibilityChange);
  lockKeyboard();
  return () => {
    active = false;
    stopSpace();
    document.documentElement.classList.remove('resolver-input-active');
    document.body.classList.remove('resolver-input-active');
    for (const name of blocks) window.removeEventListener(name, block, true);
    window.removeEventListener('keydown', keydown, true);
    window.removeEventListener('keypress', block, true);
    window.removeEventListener('keyup', keyup, true);
    window.removeEventListener('blur', onBlur);
    window.removeEventListener('click', click, true);
    window.removeEventListener('auxclick', click, true);
    document.removeEventListener('fullscreenchange', lockKeyboard);
    document.removeEventListener('visibilitychange', onVisibilityChange);
    if (keyboardOwner === owner) { keyboardOwner = null; keyboard?.unlock?.(); }
  };
}
