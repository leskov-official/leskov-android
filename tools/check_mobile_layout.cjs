// Optional live-site audit. Install Playwright separately; see docs/ANDROID_3_4_RU.md.
const {chromium}=require('playwright');
const fs=require('fs');
const path=require('path');
const root=path.resolve(__dirname,'..');
const reportDir=path.join(root,'build/reports/mobile-layout');
fs.mkdirSync(reportDir,{recursive:true});
const layout=fs.readFileSync(root+'/app/src/main/assets/leskov-app-shell.js','utf8');
const css=fs.readFileSync(root+'/app/src/main/assets/leskov-mobile.css','utf8');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.LESKOV_BROWSER || '/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const results=[];
 const context=await browser.newContext({isMobile:true,deviceScaleFactor:1,userAgent:'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36 LESKOV-Android/3.4.1'});
 const page=await context.newPage();
 for(const width of [320,360,412,768,1024]) {
  await page.setViewportSize({width,height:800});
  for(const route of ['index.php','discover.php','auth.php','terms.php','privacy_policy.php','profile.php','feed.php','messages.php']){
   try {
    await page.goto('https://lesv.online/'+route,{waitUntil:'domcontentloaded',timeout:30000});
    await page.evaluate(layout);
    await page.evaluate(()=>document.documentElement.setAttribute('data-leskov-app',''));
    await page.addStyleTag({content:css});
    await page.waitForTimeout(250);
    const dimensions=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,body:document.body.scrollWidth,title:document.title,
     overflow:Array.from(document.querySelectorAll('main, .container, .leskov-player, [role=dialog], input')).filter(el=>{const r=el.getBoundingClientRect();return r.width>0 && (r.right>innerWidth+2||r.left< -2)}).map(el=>({tag:el.tagName,cls:el.className,width:Math.round(el.getBoundingClientRect().width)}))}));
    results.push({viewport:width,route,final:page.url(),...dimensions});
    if(width===360 && ['index.php','auth.php','discover.php'].includes(route))await page.screenshot({path:path.join(reportDir,route+'.png'),fullPage:false});
   }catch(e){results.push({viewport:width,route,error:e.message})}
   fs.writeFileSync(path.join(reportDir,'results.json'),JSON.stringify(results,null,2));
  }
  console.log('checked width',width);
 }
 fs.writeFileSync(path.join(reportDir,'results.json'),JSON.stringify(results,null,2));
 const failures=results.filter(r=>r.error||r.scroll>r.width+2||r.overflow?.length);
 console.log(JSON.stringify(failures,null,2));
 if(failures.length)process.exitCode=1;
 await context.close();
 await browser.close();
})();
