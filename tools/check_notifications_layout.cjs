// Read-only fixtures of the real notification UI, with synthetic data. Requires Playwright.
const {chromium}=require('playwright'),fs=require('fs'),assert=require('assert/strict');
const path=require('path');const root=path.resolve(__dirname,'..');
(async()=>{const css=await Promise.all(['site','homepage','design-system','notifications-center','lsv-mfix'].map(async n=>{const file=path.join(process.env.LESKOV_SITE_ROOT||'/var/www/lesv.online','styles',n+'.css');return fs.existsSync(file)?fs.readFileSync(file,'utf8'):(await fetch('https://lesv.online/styles/'+n+'.min.css')).text()}));
const b=await chromium.launch({executablePath:process.env.LESKOV_BROWSER||'/usr/bin/chromium',args:['--no-sandbox','--disable-dev-shm-usage','--disable-gpu','--renderer-process-limit=1']});const p=await b.newPage();await p.route('https://lesv.online/layout-fixture',r=>r.fulfill({contentType:'text/html',body:'<html></html>'}));await p.goto('https://lesv.online/layout-fixture');
for(const width of [320,360,412]){await p.goto('https://lesv.online/layout-fixture');await p.setViewportSize({width,height:740});await p.setContent('<html data-leskov-app data-theme="light" lang="ru"><head></head><body class="app-shell utility-page notifications-page-shell">'+fs.readFileSync(root+'/tools/fixtures/notifications.html','utf8')+'</body></html>');
for(const text of [...css,fs.readFileSync(root+'/app/src/main/assets/leskov-mobile.css','utf8')])await p.addStyleTag({content:text});
await p.evaluate(()=>{window.pushRequests=[];window.LeskovNativePush={postMessage:x=>window.pushRequests.push(x)}});await p.addScriptTag({content:fs.readFileSync(root+'/app/src/main/assets/leskov-app-shell.js','utf8')});
let dims=await p.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,bad:[...document.querySelectorAll('h2,button,main,.notifications-page')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&(r.left< -1||r.right>innerWidth+1)}).map(e=>[e.className,e.textContent.slice(0,50)])}));console.log('page',width,dims);assert.equal(dims.bad.length,0);assert.ok(dims.scroll<=dims.width+2);
if(width===360)await p.screenshot({path:root+'/build/reports/mobile-layout/notifications-fixture.png'});
await p.evaluate(()=>document.querySelector('[data-notifications-prefs-modal]').hidden=false);await p.waitForTimeout(100);
const modal=await p.locator('[role=dialog]').boundingBox();console.log('modal',width,modal);assert.ok(modal.x>=0&&modal.y>=0&&modal.x+modal.width<=width+1&&modal.y+modal.height<=741);
await p.locator('[data-notifications-push-enable]').click();assert.deepEqual(await p.evaluate(()=>window.pushRequests),['request']);
if(width===360)await p.screenshot({path:root+'/build/reports/mobile-layout/notification-prefs-fixture.png'});
}await b.close();console.log('PASS authenticated notification markup: narrow layouts and native Push button');})().catch(e=>{console.error(e);process.exit(1)});
