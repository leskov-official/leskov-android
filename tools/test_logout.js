const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/leskov-app-shell.js'),'utf8');
const response = (status, data={}) => ({status,ok:status>=200&&status<300,json:async()=>data});
async function scenario(replies, expected, requestsExpected) {
  const requests=[], messages=[];
  let theme='light', scheduled, active=false;
  const root={lang:'ru',getAttribute:()=>theme,setAttribute(){}};
  const document={documentElement:root,head:{},body:{getAttribute:()=> '42',hasAttribute:()=>true,classList:{contains:()=>false}},
    querySelector(s){return s==='meta[name="viewport"]'?{content:'width=device-width, initial-scale=1, viewport-fit=cover'}:active&&s==='#callOverlay:not([hidden])'?{}:null;},
    querySelectorAll:()=>[],getElementById:()=>null,addEventListener(){}};
  const window={addEventListener(){},LeskovPageBridge:{postMessage:s=>messages.push(JSON.parse(s))},matchMedia:()=>({matches:false,addEventListener(){}})};window.top=window;
  const ctx={document,window,location:{origin:'https://lesv.online',href:'https://lesv.online/profile.php'},URL,URLSearchParams,JSON,Number,String,parseInt,
    MutationObserver:class{observe(){}},requestAnimationFrame:f=>{scheduled=f;},getComputedStyle:()=>({display:'flex'}),AbortController,setTimeout,clearTimeout,
    fetch:async(url,opts)=>{requests.push([url,opts]);let r=replies.shift();if(r instanceof Error)throw r;if(!r)throw new Error('unexpected fetch');return r;}};
  vm.runInNewContext(source,ctx);const api=window.__leskovAppShell;
  assert.equal(messages[0].lightTheme,true);theme='dark';api.refresh();assert.equal(messages.at(-1).lightTheme,false);
  active=true;assert.equal(api.logout('call'),false);active=false;
  assert.equal(api.logout('test'),true);assert.equal(api.logout('duplicate'),false);
  for(let i=0;i<30&&!messages.some(x=>x.type==='logoutResult');i++)await new Promise(r=>setImmediate(r));
  const outcome=messages.find(x=>x.type==='logoutResult');assert.ok(outcome);assert.equal(outcome.id,'test');assert.equal(outcome.confirmed,expected);
  assert.equal(requests.length,requestsExpected);
  for(const [url,opts] of requests){assert.equal(opts.credentials,'same-origin');assert.ok(url.startsWith('/'));}
  if(requests.length>=2){assert.equal(requests[1][0],'/auth.php?lang=ru');assert.equal(requests[1][1].method,'POST');assert.equal(requests[1][1].body.get('action'),'logout');assert.equal(requests[1][1].body.get('csrf_token'),'fixture-csrf');}
}
(async()=>{
 const csrf=()=>response(200,{ok:true,csrf:'fixture-csrf',user_id:42});
 await scenario([csrf(),response(200),response(401,{error:'auth_required'})],true,3);
 await scenario([csrf(),response(200),csrf()],false,3);
 await scenario([csrf(),response(403)],false,2);
 await scenario([response(200,{ok:false})],false,1);
 await scenario([new Error('offline')],false,1);
 await scenario([response(401,{error:'auth_required'})],false,1);
 await scenario([csrf(),response(200),response(401,{error:'unexpected'})],false,3);
 console.log('PASS 7 logout scenarios, duplicate/call guards and website theme propagation');
})().catch(e=>{console.error(e);process.exitCode=1;});
