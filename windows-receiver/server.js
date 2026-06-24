const { WebSocketServer } = require('ws');
const { keyboard, Key } = require('@nut-tree-fork/nut-js');

const wss = new WebSocketServer({ port: 8080 });
console.log('Receiver running on port 8080...');

const keyMap = {
  'A': Key.A,
  'B': Key.B,
  'C': Key.C
};

wss.on('connection', (ws) => {
  console.log('Phone connected!');

  ws.on('message', async (message) => {
    const key = message.toString();
    console.log('Received:', key);

    if (keyMap[key]) {
      await keyboard.type(keyMap[key]);
    }
  });

  ws.on('close', () => {
    console.log('Phone disconnected.');
  });
});