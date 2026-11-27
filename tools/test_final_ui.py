#!/usr/bin/env python3
"""Offline browser regression: both video engines, legacy controls and seasonal placement.
Optional LESKOV_SITE_CSS includes actual website CSS files in production order.
"""
import os
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
app=root/'app/src/main/assets/leskov-mobile.css'
server_only=os.environ.get('LESKOV_SERVER_ONLY')=='1'
if server_only and not os.environ.get('LESKOV_SITE_CSS'): raise SystemExit('LESKOV_SITE_CSS required for server-only test')
site='''body{margin:0}.lsv-season-controls{position:fixed;left:12px;bottom:110px!important}.lsv-season-controls[hidden],[data-home-event-reopen][hidden]{display:none!important}.lsv-season-launcher{width:56px;height:44px}.home-event-reopen{position:fixed;left:12px;bottom:84px!important;width:54px;height:54px}
html.lesv-visual-mobile body > .lsv-season-controls{bottom:150px!important}
html[data-theme] body .video-card .leskov-player.leskov-player--video:not(.leskov-player--message-circle) :is(.leskov-player__center-btn,.leskov-player__btn--play) :is(svg,svg *, .icon-pause){stroke:#fff!important;color:#fff!important}
.icon-pause,.cv-pause-icon{color:#fff}.icon-play,.cv-play-icon{color:#fff}.custom-media-player .site-chip-btn{color:#fff}.custom-media-player .site-chip-btn::before{content:'Ⅱ';color:#fff}'''
svg='<svg viewBox="0 0 24 24" stroke="currentColor" fill="none"><path d="M8 5v14M16 5v14" fill="none" stroke-width="2.6"/></svg>'
filled='<svg viewBox="0 0 24 24"><rect fill="currentColor" x="6.25" y="4.5" width="4.75" height="15" rx="1.35"/></svg>'
play='<svg viewBox="0 0 24 24"><path fill="currentColor" d="M5 4l15 8-15 8z"/></svg>'
def standard(circle=False):
 return '<div class="leskov-player leskov-player--video'+(' leskov-player--message-circle' if circle else '')+'" data-state="playing">'+''.join(f'<button class="{c}" data-action="play"><span class="icon-pause">{svg}</span><span class="icon-play">{play}</span></button>' for c in ('leskov-player__center-btn','leskov-player__btn leskov-player__btn--play'))+'</div>'
html='''<!doctype html><html data-theme="dark" class="lesv-visual-mobile"><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body data-current-user-id="0"><main style="min-height:1200px"><div class="video-card">'''+standard()+'''</div><div class="message-video-shell is-circle">'''+standard(True)+'''</div>'''+standard()+'''<div class="catalog-player" data-state="playing">'''+''.join(f'<button class="cv-btn {c}" data-cv-action="toggle"><span class="cv-pause-icon">{svg}</span><span class="cv-play-icon">{play}</span></button>' for c in ('cv-center','cv-primary'))+'''</div><div class="leskov-player leskov-player--video"><button class="leskov-player__center-btn"><span class="icon-pause">'''+filled+'''</span></button></div><div class="custom-media-player is-video is-playing" data-kind="video"><div class="custom-media-player__controls"><button class="site-chip-btn" data-media-toggle>Ⅱ</button></div></div><div class="message-video-shell is-native is-circle"><div class="custom-media-player is-video is-playing" data-kind="video"><button class="video-circle-timeline__center-btn" data-media-toggle><span class="custom-media-player__overlay-icon"><svg viewBox="0 0 44 44"><path fill="currentColor" d="M15 13h5v18h-5V13Zm9 0h5v18h-5V13Z"/></svg></span></button></div></div><div class="leskov-player leskov-player--audio"><button class="leskov-player__btn--play"><span class="icon-pause">'''+svg+'''</span></button></div></main><div class="lsv-season-controls"><button class="lsv-season-launcher">❄</button></div><button data-home-event-reopen class="home-event-reopen" hidden>★</button></body></html>'''
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'),args=['--no-sandbox','--disable-dev-shm-usage'])
 page=browser.new_page(); count=0
 for w,h in [(320,560),(412,780),(900,420)]:
  for app_first in ((False,) if server_only else (True,False)):
   page.set_viewport_size({'width':w,'height':h});page.set_content(html)
   if app_first: page.add_style_tag(path=app)
   page.add_style_tag(content=site)
   for path in os.environ.get('LESKOV_SITE_CSS','').split(os.pathsep):
    if path:page.add_style_tag(path=path)
   if not app_first and not server_only:page.add_style_tag(path=app)
   # Guest/member and several route classes, with the shared native marker.
   page.evaluate("document.documentElement.setAttribute('data-leskov-native','')")
   for uid,route in ([] if server_only else [(0,'home-page'),(0,'search-page'),(42,'home-page'),(42,'profile-page'),(42,'messages-page'),(42,'feed-page')]):
    page.evaluate('([u,r])=>{document.body.dataset.currentUserId=u;document.body.className=r}',[uid,route])
    for event in (False,True):
     page.evaluate('(show)=>document.querySelector("[data-home-event-reopen]").hidden=!show',event)
     winter=page.locator('.lsv-season-controls')
     assert winter.evaluate('(e)=>getComputedStyle(e).bottom')==('76px' if event else '12px'),(w,route,event,winter.evaluate('(e)=>getComputedStyle(e).bottom'))
     assert abs(winter.evaluate('(e)=>innerHeight-e.getBoundingClientRect().bottom')-(76 if event else 12))<1
     if event:
      assert page.locator('[data-home-event-reopen]').evaluate('(e)=>getComputedStyle(e).bottom')=='12px'
      assert winter.bounding_box()['y']+winter.bounding_box()['height']<=page.locator('[data-home-event-reopen]').bounding_box()['y']
     count+=1
   if not server_only:
    page.evaluate("document.querySelector('.lsv-season-controls').hidden=true")
    assert not page.locator('.lsv-season-controls').is_visible()
    page.evaluate("document.querySelector('.lsv-season-controls').hidden=false;document.documentElement.removeAttribute('data-leskov-native')")
    assert page.locator('.lsv-season-controls').evaluate('(e)=>getComputedStyle(e).bottom') not in ('12px','76px')
    page.evaluate("document.documentElement.setAttribute('data-leskov-native','')")
   # Compare actual glyph styles to the unpatched light theme and verify dark explicit/auto.
   for theme,scheme in [('dark','light'),('auto','dark'),('light','dark'),('auto','light')]:
    page.emulate_media(color_scheme=scheme);page.evaluate('(t)=>document.documentElement.dataset.theme=t',theme)
    dark=theme=='dark' or (theme=='auto' and scheme=='dark')
    sel='.leskov-player--video .icon-pause svg path,.catalog-player .cv-pause-icon svg path'
    values=page.locator(sel).evaluate_all('(es)=>es.map(e=>getComputedStyle(e).stroke)')
    if dark:
     assert all(x=='rgb(8, 19, 33)' for x in values),(theme,scheme,values)
     assert page.locator('.leskov-player--video .icon-pause svg rect').evaluate('(e)=>getComputedStyle(e).fill')=='rgb(8, 19, 33)'
     assert page.locator('.custom-media-player .site-chip-btn').evaluate('(e)=>getComputedStyle(e).color')=='rgb(8, 19, 33)'
     assert page.locator('.video-circle-timeline__center-btn path').evaluate('(e)=>getComputedStyle(e).fill')=='rgb(8, 19, 33)'
    else:
     assert page.locator('.catalog-player .cv-pause-icon svg path').first.evaluate('(e)=>getComputedStyle(e).stroke')!='rgb(8, 19, 33)'
    count+=1
 browser.close();print(f'PASS {count} seasonal and video theme scenarios')
