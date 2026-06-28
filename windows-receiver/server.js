const { WebSocketServer } = require('ws');
const { keyboard, Key, mouse, Point } = require('@nut-tree-fork/nut-js');
const clipboard = require('clipboardy');
const { exec } = require('child_process');

keyboard.config.autoDelayMs = 0;
mouse.config.autoDelayMs = 0;

const os = require('os');

function getLocalIP() {
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      // IPv4, not internal (skip loopback 127.0.0.1)
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return 'IP not found';
}

const wss = new WebSocketServer({ port: 8080 });
const myIP = getLocalIP();
console.log('========================================');
console.log('  Digital Keyboard receiver is running!');
console.log('  ');
console.log('  On your phone, enter this address:');
console.log('  >>>  ' + myIP + '  <<<');
console.log('  ');
console.log('  (Port 8080)');
console.log('========================================');

// Map text names to nut-js Key values
const keyMap = {
  'a': Key.A, 'b': Key.B, 'c': Key.C, 'd': Key.D, 'e': Key.E,
  'f': Key.F, 'g': Key.G, 'h': Key.H, 'i': Key.I, 'j': Key.J,
  'k': Key.K, 'l': Key.L, 'm': Key.M, 'n': Key.N, 'o': Key.O,
  'p': Key.P, 'q': Key.Q, 'r': Key.R, 's': Key.S, 't': Key.T,
  'u': Key.U, 'v': Key.V, 'w': Key.W, 'x': Key.X, 'y': Key.Y, 'z': Key.Z,
  '0': Key.Num0, '1': Key.Num1, '2': Key.Num2, '3': Key.Num3, '4': Key.Num4,
  '5': Key.Num5, '6': Key.Num6, '7': Key.Num7, '8': Key.Num8, '9': Key.Num9,
  'enter': Key.Enter, 'space': Key.Space, 'tab': Key.Tab, 'esc': Key.Escape,
  'backspace': Key.Backspace, 'delete': Key.Delete,
  'up': Key.Up, 'down': Key.Down, 'left': Key.Left, 'right': Key.Right,
  'f1': Key.F1, 'f2': Key.F2, 'f3': Key.F3, 'f4': Key.F4, 'f5': Key.F5,
  'f6': Key.F6, 'f7': Key.F7, 'f8': Key.F8, 'f9': Key.F9, 'f10': Key.F10,
  'f11': Key.F11, 'f12': Key.F12,
  'ctrl': Key.LeftControl, 'shift': Key.LeftShift,
  'alt': Key.LeftAlt, 'win': Key.LeftWin,
'playpause': Key.AudioPlay, 'pause': Key.AudioPause, 'stop': Key.AudioStop,
  'next': Key.AudioNext, 'prev': Key.AudioPrev,
  'volup': Key.AudioVolUp, 'voldown': Key.AudioVolDown, 'mute': Key.AudioMute,
  'rewind': Key.AudioRewind, 'forward': Key.AudioForward,
  'repeat': Key.AudioRepeat, 'shuffle': Key.AudioRandom,
};

const MODIFIERS = ['ctrl', 'shift', 'alt', 'win'];

async function runCombo(combo) {
  const parts = combo.toLowerCase().split('+').map(p => p.trim()).filter(p => p.length > 0);

  // Warn about any part we don't recognize
  const unknown = parts.filter(p => keyMap[p] === undefined);
  if (unknown.length > 0) {
    console.log('Unknown part(s) in combo "' + combo + '":', unknown.join(', '));
  }

  // Keep only recognized parts, then sort so modifiers are pressed first
  const valid = parts.filter(p => keyMap[p] !== undefined);
  valid.sort((a, b) => {
    const aIsMod = MODIFIERS.includes(a) ? 0 : 1;
    const bIsMod = MODIFIERS.includes(b) ? 0 : 1;
    return aIsMod - bIsMod;
  });

  const keys = valid.map(p => keyMap[p]);
  if (keys.length === 0) {
    console.log('No valid keys in combo:', combo);
    return;
  }

  try {
    await keyboard.pressKey(...keys);
    await keyboard.releaseKey(...keys);
    console.log('Sent combo:', combo, '->', valid.join('+'));
  } catch (e) {
    console.log('Error sending combo:', combo, e.message);
  }
}

async function moveMouseBy(dx, dy) {
  try {
    const pos = await mouse.getPosition();
    await mouse.setPosition(new Point(Math.round(pos.x + dx), Math.round(pos.y + dy)));
  } catch (e) {
    console.log('Mouse move error:', e.message);
  }
}

async function mouseClick() {
  try {
    await mouse.leftClick();
    console.log('Click');
  } catch (e) {
    console.log('Click error:', e.message);
  }
}

async function mouseRightClick() {
  try {
    await mouse.rightClick();
    console.log('Right click');
  } catch (e) {
    console.log('Right click error:', e.message);
  }
}

async function mouseScroll(amount) {
  try {
    if (amount > 0) {
      await mouse.scrollDown(amount);
    } else if (amount < 0) {
      await mouse.scrollUp(-amount);
    }
  } catch (e) {
    console.log('Scroll error:', e.message);
  }
}

function launchApp(target) {
  if (!target || target.trim().length === 0) {
    console.log('No app specified to launch.');
    return;
  }
  // "start" lets Windows resolve app names and paths the same way the Run dialog does
  exec('start "" "' + target + '"', (error) => {
    if (error) {
      console.log('Launch failed for "' + target + '":', error.message);
    } else {
      console.log('Launched:', target);
    }
  });
}

async function pasteText(text) {
  let original = '';
  let hadText = false;
  try {
    original = clipboard.readSync();
    hadText = (original !== null && original !== undefined && original.length >= 0);
  } catch (e) {
    // Clipboard holds something non-text (image/file) or is unreadable.
    hadText = false;
  }

  if (!hadText) {
    // Don't risk destroying a non-text clipboard — type instead of paste.
    try {
      await keyboard.type(text);
      console.log('Clipboard not text — typed instead to avoid data loss.');
    } catch (e) {
      console.log('Type fallback error:', e.message);
    }
    return;
  }

  try {
    clipboard.writeSync(text);
    await sleep(30);
    await keyboard.pressKey(Key.LeftControl, Key.V);
    await keyboard.releaseKey(Key.LeftControl, Key.V);
    await sleep(60);
  } catch (e) {
    console.log('Paste error:', e.message);
  } finally {
    try {
      clipboard.writeSync(original);
    } catch (e) {}
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

wss.on('connection', (ws) => {
  console.log('Phone connected!');

let busy = false;

ws.on('message', async (message) => {
    const raw = message.toString();

    const sep = raw.indexOf(':');
    let actionType = 'keys';
    let action = raw;
    if (sep !== -1) {
      actionType = raw.substring(0, sep);
      action = raw.substring(sep + 1);
    }

    // Mouse clicks can arrive as a picked action value too
    if (action === 'leftclick') { await mouseClick(); return; }
    if (action === 'rightclick') { await mouseRightClick(); return; }

    // Mouse moves stream rapidly — handle them immediately, bypass the busy guard and logging
if (actionType === 'move') {
      const coords = action.split(',');
      const dx = parseInt(coords[0], 10) || 0;
      const dy = parseInt(coords[1], 10) || 0;
      await moveMouseBy(dx, dy);
      return;
    }
    if (actionType === 'click') {
      await mouseClick();
      return;
    }
    if (actionType === 'scroll') {
      const amount = parseInt(action, 10) || 0;
      await mouseScroll(amount);
      return;
    }
    if (actionType === 'rightclick') {
      await mouseRightClick();
      return;
    }

    console.log('Received:', raw);

    if (busy) {
      console.log('Busy, ignoring:', raw);
      return;
    }
    busy = true;
    try {
      if (actionType === 'text') {
        await pasteText(action);
        console.log('Pasted text:', action);
      } else if (actionType === 'launch') {
        launchApp(action);
      } else {
        await runCombo(action);
      }
    } finally {
      busy = false;
    }
  });

  ws.on('close', () => console.log('Phone disconnected.'));
});