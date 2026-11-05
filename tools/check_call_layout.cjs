// Read-only browser audit of actual call markup. Requires separately installed Playwright.
const {chromium}=require('playwright');const fs=require('fs');const path=require('path');const assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
(async()=>{
 const styles=await Promise.all(['site','homepage','calls'].map(async name=>{
  const response=await fetch('https://lesv.online/styles/'+name+'.min.css');
  if(!response.ok)throw Error('Styles unavailable: '+name);return response.text();
 }));
 const mobile=fs.readFileSync(path.join(root,'app/src/main/assets/leskov-mobile.css'),'utf8');
 const markup=fs.readFileSync(path.join(root,'tools/fixtures/calls.html'),'utf8');
 const reports=path.join(root,'build/reports/call-layout');fs.mkdirSync(reports,{recursive:true});
 const browser=await chromium.launch({executablePath:process.env.LESKOV_BROWSER||'/usr/bin/chromium',args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage();let checks=0;
 for(const [width,height] of [[320,568],[360,740],[412,800],[740,360]]){
  await page.setViewportSize({width,height});
  for(const theme of ['light','dark']){
   await page.setContent(`<html data-leskov-app data-theme="${theme}" lang="ru"><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>${markup}</body></html>`);
   for(const css of [...styles,mobile])await page.addStyleTag({content:css});
   for(const selector of ['#incomingCallModal','#callOverlay','[data-call-incoming]','[data-call-active]']){
    await page.evaluate(s=>{document.querySelectorAll('#incomingCallModal,#callOverlay,[data-call-incoming],[data-call-active]').forEach(el=>el.hidden=true);const el=document.querySelector(s);el.hidden=false;},selector);
    const result=await page.evaluate(s=>{
     const root=document.querySelector(s);const r=root.getBoundingClientRect();
     const bad=Array.from(root.querySelectorAll('button')).filter(el=>{const b=el.getBoundingClientRect();return b.width>0&&(b.left< -1||b.right>innerWidth+1||el.scrollWidth>el.clientWidth+2)}).map(el=>el.textContent.trim());
     return {overlay:{left:r.left,top:r.top,width:r.width,height:r.height},bad};
    },selector);
    assert.equal(result.bad.length,0,JSON.stringify({width,height,theme,selector,...result}));
    assert.ok(Math.abs(result.overlay.width-width)<=1 && Math.abs(result.overlay.height-height)<=1,JSON.stringify({width,height,selector,...result}));
    checks++;
    if(width===360&&theme==='light')await page.screenshot({path:path.join(reports,selector.replace(/[^a-zA-Z]/g,'')+'.png')});
   }
  }
 }
 console.log(`PASS call layout: ${checks} cases, both call engines, light/dark, portrait/landscape`);
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
