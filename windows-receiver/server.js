const { WebSocketServer } = require('ws');
const { keyboard, Key } = require('@nut-tree-fork/nut-js');

keyboard.config.autoDelayMs = 0;

const wss = new WebSocketServer({ port: 8080 });
console.log('Receiver running on port 8080...');

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
  'alt': Key.LeftAlt, 'win': Key.LeftWin
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

wss.on('connection', (ws) => {
  console.log('Phone connected!');

  ws.on('message', async (message) => {
    const action = message.toString();
    console.log('Received:', action);
    await runCombo(action);
  });

  ws.on('close', () => console.log('Phone disconnected.'));
});