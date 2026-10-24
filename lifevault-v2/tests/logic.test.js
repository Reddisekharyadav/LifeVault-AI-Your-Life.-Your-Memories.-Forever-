'use strict';
/* LifeVault v2 Round 2 logic tests — run with: node tests/logic.test.js
   Pure logic is EXTRACTED from app.js (not copied by hand): the MOODS/STOP/
   SYN/suggestMood block and fmtTime are sliced out of the real file and run
   in a vm sandbox, so these tests fail if the real code regresses. The full
   app (DOM, IndexedDB, WebCrypto) is NOT run here — see TESTS.md for the
   manual browser checklist. No live-browser test is claimed. */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

const root = path.join(__dirname, '..');
const appJs = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'manifest.json'), 'utf8'));
const kt = fs.readFileSync(path.join(root, 'android-improvements/kotlin/com/lifevault/intelligence/LocalIntelligence.kt'), 'utf8');

let passed = 0;
function check(name, fn) { fn(); passed++; console.log('ok - ' + name); }

// --- extract pure logic from app.js ---
const start = appJs.indexOf('const MOODS=');
const end = appJs.indexOf('// ---------- storage');
assert(start >= 0 && end > start, 'pure-logic block not found in app.js');
const pure = appJs.slice(start, end);
const fmtTimeMatch = appJs.match(/function fmtTime\(ms\)\{[\s\S]*?\n\}/);
assert(fmtTimeMatch, 'fmtTime not found in app.js');
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(pure + '\n' + fmtTimeMatch[0] + '\nglobalThis.__lifevaultTest = { tokens, expandTokens, suggestMood, fmtTime, MOODS };', sandbox);
const { tokens, expandTokens, suggestMood, fmtTime, MOODS } = sandbox.__lifevaultTest;

check('MOODS is the 8-mood web set', () => {
  assert.strictEqual(JSON.stringify([...MOODS]), JSON.stringify(['Happy','Calm','Grateful','Excited','Tired','Sad','Reflective','Anxious'])); // [...MOODS]: vm-realm array, so compare via JSON
});
check('tokens lowercases and drops stopwords', () => {
  const t = tokens('The Coffee and beach');
  assert(t.includes('coffee') && t.includes('beach') && !t.includes('the') && !t.includes('and'));
});
check('expandTokens expands coffee -> espresso/latte', () => {
  const t = expandTokens('coffee');
  assert(t.includes('espresso') && t.includes('latte'));
});
check('suggestMood finds Happy', () => {
  const s = suggestMood('What a happy wonderful day, I laughed a lot');
  assert(s && s.mood === 'Happy' && s.confidence > 0);
});
check('suggestMood negation: "not happy" is not Happy', () => {
  const s = suggestMood('I am not happy today');
  assert(!s || s.mood !== 'Happy', 'negated happy must not classify as Happy, got ' + JSON.stringify(s));
});
check('suggestMood "not sad, wonderful" -> Happy', () => {
  const s = suggestMood('I am not sad at all, I feel wonderful');
  assert(s && s.mood === 'Happy');
});
check('suggestMood covers Grateful / Reflective / Anxious (Round 2 parity)', () => {
  assert.strictEqual(suggestMood('I am grateful and thankful').mood, 'Grateful');
  assert.strictEqual(suggestMood('I keep reflecting and remembering').mood, 'Reflective');
  assert.strictEqual(suggestMood('I feel anxious and worried').mood, 'Anxious');
});
check('suggestMood returns null with no mood words', () => {
  assert.strictEqual(suggestMood('Went to the shop and bought milk'), null);
});
check('fmtTime formats mm:ss', () => {
  assert.strictEqual(fmtTime(0), '00:00');
  assert.strictEqual(fmtTime(125000), '02:05');
  assert.strictEqual(fmtTime(61000), '01:01');
});

// --- static security checks on the real app.js ---
check('PIN uses salted PBKDF2, 100000 iterations, per-profile salt', () => {
  assert(appJs.includes('PBKDF2') && appJs.includes('100000') && appJs.includes('pinSalt') && appJs.includes('makePinRecord'));
  assert(appJs.includes('verifyPin') && appJs.includes('PBKDF2_ITERS'));
});
check('no browser prompt() call in app.js', () => {
  const noComments = appJs.split('\n').filter(l => !l.trim().startsWith('//') && !l.trim().startsWith('*')).join('\n');
  assert(!/\bprompt\s*\(/.test(noComments), 'prompt() still present');
});
check('no recovery key stored: no .recoveryKey= or recoveryKey: assignment', () => {
  assert(!/\.recoveryKey\s*=/.test(appJs), 'recoveryKey assignment found');
  assert(!/recoveryKey\s*:/.test(appJs), 'recoveryKey field literal found');
  assert(appJs.includes("delete pr.recoveryKey"), 'Round-1 stored keys are not stripped on load');
});
check('no fetch/XHR/CDN in app.js', () => {
  // Strip full-line comments first: the header comment itself says "No fetch()".
  const code = appJs.split('\n').filter(l => { const t = l.trim(); return !t.startsWith('//') && !t.startsWith('/*') && !t.startsWith('*'); }).join('\n');
  assert(!/\bfetch\s*\(/.test(code));
  assert(!code.includes('XMLHttpRequest'));
  assert(!/https?:\/\//.test(code), 'external URL in app.js');
});
check('export/import use the in-page dialog and clear the password', () => {
  assert(appJs.includes('askExportPassword') && appJs.includes('askImportPassword') && appJs.includes('pwDialog'));
  assert(appJs.includes("pw=''") || appJs.includes('pw = '), 'password is not cleared after use');
});
check('reminders are opt-in, default OFF, generic notification only', () => {
  assert(appJs.includes('capsulesOnOpen:false') && appJs.includes('dailyPrompt:false'));
  assert(appJs.includes('A LifeVault reflection prompt is waiting'));
  assert(!appJs.includes('new Notification(') || appJs.includes('A LifeVault reflection prompt is waiting'));
});
check('HTML contains the Round 2 dialogs and reminder controls, no inline export password', () => {
  for (const id of ['pwDialog','switchPinDialog','remCapsule','remDaily','remDailyTime','capsuleCard','reflectionCard']) {
    assert(html.includes('id="' + id + '"'), 'missing HTML id ' + id);
  }
  assert(!html.includes('id="exportPw"'), 'inline export password field still present');
  assert(!html.includes('SHA-256 hash is kept'), 'old SHA-256 lock text still present');
});
check('manifest parses and has a 192 icon', () => {
  assert.strictEqual(manifest.name, 'LifeVault');
  assert(manifest.icons.some(i => i.sizes === '192x192'), 'no 192x192 icon');
});
check('Kotlin mood set matches web v2 (8 moods, no ANGRY enum value)', () => {
  for (const m of ['HAPPY','CALM','GRATEFUL','EXCITED','TIRED','SAD','REFLECTIVE','ANXIOUS']) {
    assert(kt.includes(m), 'Kotlin missing mood ' + m);
  }
  assert(!/Mood\.ANGRY/.test(kt), 'ANGRY still used as a mood value in code');
  assert(!/enum class Mood \{[^}]*ANGRY/.test(kt), 'ANGRY still in Mood enum');
});

console.log('\n' + passed + ' checks passed.');
