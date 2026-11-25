const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/leskov-app-shell.js'),'utf8');
let checks=0;
function check(actual,expected){assert.deepEqual(actual,expected);checks++;}
function fixture(origin='https://lesv.online', fetcher){
 const events={},states=[],push=[],viewport={content:''},button={disabled:true,setAttribute(){},classList:{toggle(){}}},status={textContent:'Web Push unsupported'};
 let active=false,accepted=true,calls=0;const navigated=[];
 const document={documentElement:{lang:'ru',setAttribute(){}},head:{},body:{getAttribute:()=> '42',hasAttribute:()=>true,classList:{contains:()=>false}},
  querySelector(selector){if(selector==='meta[name="viewport"]')return viewport; if(active&&selector==='#callOverlay:not([hidden])')return {};return null;},
  querySelectorAll(selector){if(selector==='[data-notifications-push-enable]')return [button];if(selector==='[data-notifications-push-status]')return [status];return [];},
  getElementById:()=>null,addEventListener:(name,fn)=>{events[name]=fn;}};
 const window={addEventListener(){},LeskovPageBridge:{postMessage:s=>states.push(JSON.parse(s))},
  LeskovNativePush:{postMessage:s=>push.push(s)},__leskovNavigateWithPlayer(){calls++;return accepted;}};
 window.top=window;
 vm.runInNewContext(source,{document,window,location:{origin,href:origin+'/index.php',replace:url=>navigated.push(url)},fetch:fetcher,URL,URLSearchParams,JSON,Number,String,parseInt,
  MutationObserver:class{observe(){}},requestAnimationFrame(){},getComputedStyle:()=>({display:'flex'})});
 return {window,button,status,viewport,states,push,events,navigated,setActive:v=>active=v,setAccepted:v=>accepted=v,calls:()=>calls,api:window.__leskovAppShell};
}
const f=fixture();
check(f.button.disabled,false);
check(f.viewport.content,'width=device-width, initial-scale=1, viewport-fit=cover');
check(f.states[0].userId,42);check(f.states[0].callActive,false);
check(f.api.navigate('/feed.php'),'soft');check(f.calls(),1);
f.setActive(true);check(f.api.navigate('/messages.php'),'call_active');check(f.calls(),1);
f.api.refresh();check(f.states.at(-1).callActive,true);
f.setActive(false);f.setAccepted(false);check(f.api.navigate('/auth.php'),'hard');
for(const url of ['https://evil.test/','http://lesv.online/','https://user:pass@lesv.online/','javascript:alert(1)'])check(f.api.navigate(url),'hard');
f.api.setPushStatus('Registered');check(f.status.textContent,'Registered');
f.status.textContent='Unsupported again';f.button.disabled=true;f.api.refresh();
check(f.status.textContent,'Registered');check(f.button.disabled,false);
let prevented=0;
f.events.click({target:{closest:()=>f.button},preventDefault(){prevented++;},stopImmediatePropagation(){prevented++;}});
check(f.push,['request']);check(prevented,2);
f.api.setPushStatus({enabled:true,status:'ready',message:'Enabled'});
check(f.button.disabled,false);check(f.button.textContent,'✓ Push-уведомления включены');
f.events.click({target:{closest:()=>f.button},preventDefault(){},stopImmediatePropagation(){}});
check(f.push,['request','settings']);
f.api.setPushStatus({enabled:false,status:'connecting',message:'Connecting'});check(f.button.disabled,true);
f.api.setPushStatus({enabled:false,status:'permission_denied',message:'Disabled'});check(f.button.disabled,false);
check(f.button.textContent,'Включить Push на этом устройстве');
check(fixture('https://evil.test').api,undefined);
async function testSignOut() {
 for(const [initial,after,success] of [[200,401,true],[200,200,false],[401,401,true],[503,401,false]]){
  const requests=[];
  const test=fixture('https://lesv.online',async(url,options)=>{
   requests.push([url,options]);
   if(url==='/auth.php') return {ok:true};
   const status=requests.length===1?initial:after;
   return {status,ok:status===200,json:async()=>({ok:true,csrf:'fixture-csrf'})};
  });
  const task=test.api.signOut();check(task===test.api.signOut(),true);
  check(await task,success);check(test.navigated,success?['/auth.php']:[]);
  check(test.states.at(-1).type,success?'signed_out':'notice');
  if(initial===200){
   check(requests.map(item=>item[0]),['/mobile_push.php?action=csrf','/auth.php','/mobile_push.php?action=csrf']);
   const options=requests[1][1];check(options.method,'POST');check(options.credentials,'same-origin');
   check(options.body.get('csrf_token'),'fixture-csrf');check(options.body.get('action'),'logout');
  }else check(requests.length,1);
 }
}
testSignOut().then(()=>console.log(`PASS app shell: ${checks} assertions (navigation, calls, FCM, origin, POST/CSRF sign-out)`))
 .catch(error=>{console.error(error);process.exitCode=1});
