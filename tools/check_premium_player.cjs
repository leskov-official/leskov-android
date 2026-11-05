// Browser compatibility test of the actual site Player class with local generated WAV audio.
// This fixture does not sign into a Premium account or test paid streaming authorization.
const {chromium}=require('playwright');const fs=require('fs');const path=require('path');const assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
(async()=>{
 const response=await fetch('https://lesv.online/scripts/player.min.js');
 if(!response.ok)throw Error('Website player unavailable: '+response.status);
 const siteScript=await response.text();
 const b=await chromium.launch({executablePath:process.env.LESKOV_BROWSER || '/usr/bin/chromium',args:['--no-sandbox','--disable-dev-shm-usage','--autoplay-policy=no-user-gesture-required']});
 const page=await b.newPage({viewport:{width:360,height:720}});
 const samples=22050*30;const wav=Buffer.alloc(44+samples*2);wav.write('RIFF');wav.writeUInt32LE(36+samples*2,4);wav.write('WAVEfmt ',8);wav.writeUInt32LE(16,16);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(22050,24);wav.writeUInt32LE(44100,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34);wav.write('data',36);wav.writeUInt32LE(samples*2,40);
 await page.route('https://lesv.online/**',route=>route.fulfill(route.request().url().endsWith('.wav')?{status:200,contentType:'audio/wav',body:wav}:{status:200,contentType:'text/html',body:'<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div class="leskov-player leskov-player--premium" data-title="Premium One" data-artist="LESKOV" data-src="/one.wav"><audio class="leskov-player__media" src="/one.wav"></audio></div></body></html>'}));
 await page.goto('https://lesv.online/test');
 await page.addStyleTag({content:fs.readFileSync(path.join(root,'app/src/main/assets/leskov-mobile.css'),'utf8')});
 await page.evaluate(()=>document.documentElement.setAttribute('data-leskov-app',''));
 await page.evaluate(()=>{window.states=[];window.LeskovAudioBridge={postMessage:s=>window.states.push(JSON.parse(s))};});
 await page.addScriptTag({content:fs.readFileSync(path.join(root,'app/src/main/assets/leskov-player-bridge.js'),'utf8')});
 await page.addScriptTag({content:siteScript});
 await page.evaluate(async()=>{let p=window.LeskovPlayer.Manager.instances.values().next().value;if(!p)p=new window.LeskovPlayer.Player(document.querySelector('.leskov-player'));window.p=p;p.setPlaylist([{src:'/one.wav',title:'Premium One',artist:'LESKOV'},{src:'/two.wav',title:'Premium Two',artist:'LESKOV'}]);await p.play();});
 await page.waitForFunction(()=>window.states.some(s=>s.playing&&s.title==='Premium One'));
 await page.evaluate(()=>window.__leskovAndroidAudio.command('pause'));
 await page.waitForFunction(()=>window.states.at(-1).playing===false);
 assert.equal(await page.evaluate(()=>window.states.at(-1).title),'Premium One');
 await page.evaluate(()=>window.__leskovAndroidAudio.command('play'));
 await page.waitForFunction(()=>window.states.at(-1).playing===true);
 await page.evaluate(()=>window.__leskovAndroidAudio.command('seek:5000'));
 await page.waitForFunction(()=>window.states.at(-1).positionMs>=5000);
 await page.evaluate(()=>window.__leskovAndroidAudio.command('next'));
 await page.waitForFunction(()=>window.states.at(-1).title==='Premium Two'&&window.states.at(-1).playing);
 const snapshot=await page.evaluate(()=>JSON.parse(window.__leskovAndroidAudio.snapshot()));assert.equal(snapshot.queueIndex,1);assert.equal(snapshot.queue.length,2);
 await page.evaluate(()=>p.media.remove());await page.waitForTimeout(100);await page.evaluate(()=>p.play());await page.waitForFunction(()=>!p.media.paused);await page.waitForTimeout(1600);
 assert.equal(await page.evaluate(()=>JSON.parse(window.__leskovAndroidAudio.snapshot()).title),'Premium Two');
 await page.evaluate(()=>window.__leskovAndroidAudio.command('pause'));await page.waitForFunction(()=>window.states.at(-1).playing===false);
 await page.evaluate(()=>{
  window.nativeActions=[];window.LeskovNativePlayback={postMessage:s=>window.nativeActions.push(s)};
  window.__leskovNativePlayback={active:true,playing:false,buffering:true,title:'Background track',artist:'LESKOV'};
  window.__leskovAndroidAudio.renderNative();
 });
 assert.equal(await page.locator('#leskov-native-playback-badge').getAttribute('aria-busy'),'true');
 await page.locator('#leskov-native-playback-badge').click();
 assert.deepEqual(await page.evaluate(()=>window.nativeActions),['toggle']);
 await page.evaluate(()=>{window.__leskovNativePlayback.active=false;window.__leskovAndroidAudio.renderNative()});
 assert.equal(await page.locator('#leskov-native-playback-badge').count(),0);
 console.log('PASS real website Premium Player class + bridge: play, pause, resume, seek, next, queue, detached media, native status');
 // A second fixture runs the site's independent header engine, without LeskovPlayer.
 const siteResponse=await fetch('https://lesv.online/scripts/site.min.js');
 if(!siteResponse.ok)throw Error('Header engine unavailable: '+siteResponse.status);
 const headerScript=await siteResponse.text();
 const header=await b.newPage({viewport:{width:320,height:720}});
 const headerHtml=`<html lang="ru"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
 <body data-user-premium-active="1"><button data-header-player-toggle>Player</button>
 <div id="headerPlayerPanel" class="header-player-panel" data-player-mode="sequential">
 <div class="header-player-dialog"><div class="header-player-shell"><div data-header-player data-player-mode="sequential">
 <audio src="/one.wav" preload="auto"></audio><img data-header-cover src="/cover.webp">
 <strong data-header-title>Header One</strong><span data-header-artist>LESKOV</span>
 <button data-header-prev>Previous</button><button data-header-play>Play</button><button data-header-next>Next</button>
 <input type="range" min="0" max="100" data-header-range><span data-header-current></span><span data-header-duration></span>
 </div><div class="header-player-playlist">
 <button class="is-current" data-header-player-track data-audio="/one.wav" data-title="Header One" data-artist="LESKOV">One</button>
 <button data-header-player-track data-audio="/two.wav" data-title="Header Two" data-artist="LESKOV">Two</button>
 </div></div></div></div></body></html>`;
 await header.route('https://lesv.online/**',route=>route.fulfill(route.request().url().endsWith('.wav')?
  {status:200,contentType:'audio/wav',body:wav}:{status:200,contentType:'text/html',body:headerHtml}));
 await header.goto('https://lesv.online/header-fixture');
 await header.evaluate(()=>{window.states=[];window.LeskovAudioBridge={postMessage:s=>window.states.push(JSON.parse(s))};});
 await header.addScriptTag({content:fs.readFileSync(path.join(root,'app/src/main/assets/leskov-player-bridge.js'),'utf8')});
 await header.addScriptTag({content:headerScript});
 await header.waitForFunction(()=>!!window.__leskovHeaderPlayer);
 await header.evaluate(()=>document.querySelector('[data-header-play]').click());
 await header.waitForFunction(()=>window.states.some(s=>s.playing&&s.title==='Header One'));
 await header.evaluate(()=>window.__leskovAndroidAudio.command('pause'));
 await header.waitForFunction(()=>window.states.at(-1).playing===false);
 assert.equal(await header.evaluate(()=>window.states.at(-1).title),'Header One');
 await header.evaluate(()=>window.__leskovAndroidAudio.command('play'));
 await header.waitForFunction(()=>window.states.at(-1).playing===true);
 await header.evaluate(()=>window.__leskovAndroidAudio.command('seek:5000'));
 await header.waitForFunction(()=>window.states.at(-1).positionMs>=5000);
 await header.evaluate(()=>window.__leskovAndroidAudio.command('next'));
 await header.waitForFunction(()=>window.states.at(-1).title==='Header Two'&&window.states.at(-1).playing);
 const headerSnap=await header.evaluate(()=>JSON.parse(window.__leskovAndroidAudio.snapshot()));
 assert.equal(headerSnap.queueIndex,1);assert.equal(headerSnap.queue.length,2);
 console.log('PASS real website header Premium engine + bridge: play, pause, resume, seek, next, queue');
 await b.close();
})().catch(e=>{console.error(e);process.exit(1)});
