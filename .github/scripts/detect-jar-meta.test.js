#!/usr/bin/env node
'use strict';

const assert = require('assert');
const { detect, detectRequired } = require('./detect-jar-meta.js');

const cases = [
  // Current MultiLoader naming
  ['livingworld-fabric-1.20.1-1.0.0.jar', 'fabric', '1.20.1'],
  ['livingworld-forge-1.20.1-1.0.0.jar', 'forge', '1.20.1'],
  ['livingworld-fabric-1.21.1-1.0.0.jar', 'fabric', '1.21.1'],
  ['livingworld-neoforge-1.21.1-1.0.0.jar', 'neoforge', '1.21.1'],
  ['livingworld-fabric-26.2-1.0.0.jar', 'fabric', '26.2'],
  ['livingworld-neoforge-26.2-1.0.0.jar', 'neoforge', '26.2'],
  ['livingworld-fabric-26.3-1.0.0.jar', 'fabric', '26.3'],
  ['livingworld-neoforge-26.3-1.0.0.jar', 'neoforge', '26.3'],
  ['releases/livingworld-neoforge-26.3-1.0.0.jar', 'neoforge', '26.3'],
];

let failed = 0;
for (const [name, loader, gv] of cases) {
  const got = detect(name);
  try {
    assert.strictEqual(got.loader, loader, `${name} loader`);
    assert.strictEqual(got.gv, gv, `${name} gv`);
    console.log('ok', name, '->', got);
  } catch (e) {
    failed += 1;
    console.error('FAIL', name, 'got', got, e.message);
  }
}

// Unparseable names must fail instead of falling back to a default Minecraft version.
try {
  detectRequired('livingworld-fabric-mystery.jar');
  failed += 1;
  console.error('FAIL expected throw for mystery jar');
} catch (e) {
  console.log('ok refuses default fallback:', e.message.split('\n')[0]);
}

if (failed) {
  console.error(`${failed} case(s) failed`);
  process.exit(1);
}
console.log('all detect-jar-meta cases passed');
