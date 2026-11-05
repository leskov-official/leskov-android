// Run with node tools/test_player_bridge.js. No network, browser or npm packages required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/leskov-player-bridge.js'), 'utf8');
let assertions = 0;
function check(actual, expected) { assert.deepEqual(actual, expected); assertions++; }
function fixture({dom = [], active = null, instances = [], header = null, headerAudio = null, origin = 'https://lesv.online'} = {}) {
    const messages = [], docEvents = {}, windowEvents = {};
    let tick, tasks = [];
    const document = {
        baseURI: origin + '/profile.php',
        querySelectorAll: () => dom,
        querySelector: selector => selector === '[data-header-player]' && header ? header.root : null,
        getElementById: id => id === 'headerPlayerPanel' && header ? header.panel : null,
        addEventListener: (name, fn) => { docEvents[name] = fn; }
    };
    const window = {
        __leskovPlayerManager: {active, instances: new Set(instances)},
        __leskovHeaderPlayerAudio: headerAudio,
        LeskovAudioBridge: {postMessage: raw => messages.push(JSON.parse(raw))},
        addEventListener: (name, fn) => { windowEvents[name] = fn; }
    };
    window.top = window;
    const context = {window, document, location:{origin}, navigator:{mediaSession:{metadata:{title:'Stale title'}}},
        URL, WeakSet, JSON, Array, Math, Number, String, Boolean,
        setTimeout: fn => { tasks.push(fn); return tasks.length; }, setInterval: fn => { tick = fn; }};
    vm.runInNewContext(source, context, {timeout:1000});
    return {window, messages, document, tick: () => tick(), flush: () => { const batch = tasks; tasks = []; batch.forEach(fn => fn()); },
        event: (name, target) => docEvents[name]({type:name, target}),
        api: window.__leskovAndroidAudio};
}
function audio(src = 'https://lesv.online/tracks/premium.mp3', overrides = {}) {
    return {tagName:'AUDIO', paused:false, ended:false, currentSrc:src, currentTime:42, duration:180, srcObject:null,
        title:'Fallback title', listeners:{}, getAttribute: () => src, closest: () => null,
        addEventListener(name, fn) { this.listeners[name] = fn; },
        play() { this.paused = false; return Promise.resolve(); }, pause() { this.paused = true; }, ...overrides};
}

// Premium media can be detached: source of truth is the site's manager, not DOM order.
const media = audio();
let next = 0, prev = 0;
const player = {media, title:'Premium track', artist:'Igor', cover:'/cover.webp',
    playlist:[{src:'/tracks/premium.mp3',title:'Premium track'},{src:'/tracks/second.mp3',title:'Second'}],
    playlistIndex:0, mode:'shuffle',
    next() { next++; }, prev() { prev++; }, pause() { media.pause(); }, play() { return media.play(); },
    seek(pos) { media.currentTime = pos; }};
const f = fixture({active:player, instances:[player]});
check(f.messages[0].title, 'Premium track');
check(f.messages[0].artist, 'Igor');
check(f.messages[0].artwork, 'https://lesv.online/cover.webp');
check(f.messages[0].positionMs, 42000);
check(f.messages[0].durationMs, 180000);
check(f.messages[0].hasNext, true);
check(f.messages[0].hasPrevious, true);
const snap = JSON.parse(f.api.snapshot());
check(snap.url, media.currentSrc);
check(snap.queue.length, 2);
check(snap.mode, 'shuffle');
check(snap.queueIndex, 0);
check(f.api.command('next'), true); check(next, 1);
check(f.api.command('previous'), true); check(prev, 1);
check(f.api.command('seek:90000'), true); check(media.currentTime, 90);
f.api.command('seek:999999'); check(media.currentTime, 180);
check(f.api.command('seek:-2'), false);
check(f.api.command('unknown'), false);
f.api.command('pause'); f.flush();
check(f.messages.at(-1).playing, false);
check(f.messages.at(-1).title, 'Premium track');
check(f.messages.at(-1).positionMs, 180000);
check(f.api.snapshot(), null);
f.api.command('play'); f.flush(); check(f.messages.at(-1).playing, true);

// Ringtones and WebRTC must never stop native music or appear as its notification.
for (const a of [audio(''),audio('https://evil.test/song.mp3'),audio('https://lesv.online/call_ringtone.wav'),audio('https://lesv.online/uploads/notification_chime.wav'),
    audio(undefined,{srcObject:{}}),audio(undefined,{tagName:'VIDEO'}),
    audio('https://user:pass@lesv.online/a.mp3'),audio('http://lesv.online/a.mp3')]) {
    const rejected = fixture({dom:[a]});
    rejected.event('play',a); rejected.flush();
    check(rejected.messages.length, 0); check(rejected.api.snapshot(), null);
}
// Blob audio remains controllable in WebView, but is never sent to Media3 as an HTTPS stream.
const blob = fixture({dom:[audio('blob:https://lesv.online/123')]});
check(blob.messages[0].playing,true); check(blob.api.snapshot(),null);

// Discover a manager created after script installation and retain paused track details.
const late = fixture(); check(late.messages.length,0);
const lateMedia = audio();
late.window.__leskovPlayerManager.active = {media:lateMedia,title:'Late Premium'};
late.tick(); check(late.messages.at(-1).title,'Late Premium');
check(typeof lateMedia.listeners.play,'function');
lateMedia.paused = true; late.event('pause',lateMedia); late.flush();
check(late.messages.at(-1).playing,false);
check(late.messages.at(-1).title,'Late Premium');

// Invalid, unselected, or oversized queues must not change which song resumes.
for (const playlist of [[{src:'https://evil.test/a.mp3'}], Array(101).fill({src:media.currentSrc}),
    [{src:'/tracks/other.mp3'}], [{src:media.currentSrc},{src:'/call_ringtone.wav'}]]) {
    const test = fixture({active:{...player,playlist}});
    check(JSON.parse(test.api.snapshot()).queue,undefined);
}
const single = fixture({active:{...player,playlist:[]}});
check(single.messages[0].hasNext,false); check(single.api.command('next'),false);
// The header panel is a separate Premium engine without manager.active/instances.
const headerMedia = audio('https://lesv.online/header/one.mp3');
const headerTitle = {textContent:'Header One'};
const headerTracks = [{dataset:{audio:'/header/one.mp3',title:'Header One',artist:'Igor'}},
    {dataset:{audio:'/header/two.mp3',title:'Header Two',artist:'Igor'}}];
let headerToggles = 0;
const headerRoot = {dataset:{playerMode:'repeat'}, querySelector(selector) {
    if (selector === 'audio') return headerMedia;
    if (selector === '[data-header-title]') return headerTitle;
    if (selector === '[data-header-artist]') return {textContent:'Header Artist'};
    if (selector === '[data-header-cover]') return {src:'https://lesv.online/header/cover.webp'};
    if (selector === '[data-header-play]') return {click() { headerToggles++; headerMedia.paused = !headerMedia.paused; }};
    if (selector === '[data-header-next]') return {click() { headerMedia.currentSrc='https://lesv.online/header/two.mp3'; headerTitle.textContent='Header Two'; }};
    return null;
}};
const headerPanel = {dataset:{}, querySelectorAll: () => headerTracks};
const h = fixture({header:{root:headerRoot,panel:headerPanel},headerAudio:headerMedia});
check(h.messages[0].title,'Header One'); check(h.messages[0].artist,'Header Artist');
check(h.messages[0].hasNext,true);
const headerSnapshot = JSON.parse(h.api.snapshot());
check(headerSnapshot.queue.length,2); check(headerSnapshot.mode,'repeat');
h.api.command('pause');h.flush();check(headerToggles,1);check(h.messages.at(-1).playing,false);
h.api.command('play');h.flush();check(headerToggles,2);check(h.messages.at(-1).playing,true);
h.api.command('next');h.flush();check(h.messages.at(-1).title,'Header Two');
check(JSON.parse(h.api.snapshot()).queueIndex,1);
h.api.command('seek:5000');check(headerMedia.currentTime,5);

// A real user press must release native audio focus before the site's click handler plays.
const pointerButton = {disabled:false,getAttribute:()=>null,closest:()=>headerRoot};
h.event('pointerdown',{closest:()=>pointerButton});check(h.messages.at(-1).type,'web_start');
const beforeDisabled = h.messages.length; pointerButton.disabled=true;
h.event('pointerdown',{closest:()=>pointerButton});check(h.messages.length,beforeDisabled);

const foreign = fixture({origin:'https://evil.test'}); check(foreign.api,undefined);
console.log(`PASS player bridge: ${assertions} assertions (Premium, detached media, pause/seek, queue, calls, origin)`);
