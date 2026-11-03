// An offline, deterministic smoke test for the JavaScript handoff eligibility rules.
// This is NOT a substitute for Android playback testing or a real streaming endpoint.
const fs = require('fs');
const vm = require('vm');
const path = require('path');
const src = fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/example/media/LeskovAudioHandoff.kt'), 'utf8');
const m = src.match(/private val snapshotJs = """([\s\S]*?)"""\.trimIndent\(\)/);
if(!m) throw new Error('snapshotJs not found');
function evaluate(audio) {
  const doc = {
    baseURI:'https://lesv.online/index.php', title:'LESKOV',
    querySelectorAll(selector){return selector === 'audio' ? [audio] : []}
  };
  const ctx = {document:doc,window:{},URL,JSON,Array,Math};
  return vm.runInNewContext(m[1], ctx, {timeout:1000});
}
function track(currentSrc){ return {paused:false,ended:false,srcObject:null,currentSrc,src:currentSrc,currentTime:42,title:'Test audio'} }
const assertions = [
  [evaluate(track('')), null, 'must reject empty src (current PHP document is not a track)'],
  [evaluate(track('https://evil.test/a.mp3')), null, 'must reject foreign origins'],
  [evaluate(track('https://lesv.online/media/call_ringtone.wav')), null, 'must reject call ringtone'],
  [evaluate({...track(''),srcObject:{}}), null, 'must reject WebRTC srcObject'],
];
const good = evaluate(track('https://lesv.online/tracks/a.mp3'));
if(typeof good !== 'string' || JSON.parse(good).url !== 'https://lesv.online/tracks/a.mp3') {
  throw new Error('valid first-party audio not captured');
}
for(const [actual,expected,description] of assertions) {
  if(actual !== expected) throw new Error(description);
}
console.log('PASS audio eligibility: empty, foreign, ringtone, WebRTC rejected; first-party track accepted');
