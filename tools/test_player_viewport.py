#!/usr/bin/env python3
"""Offline DOM contract: viewport stays covered while the playlist scrolls."""
from pathlib import Path
import os
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
site='''body{margin:0}.header-player-panel{position:fixed;inset:0;overflow:auto!important}.header-player-backdrop{position:absolute;inset:0;background:#2228}.header-player-dialog{position:relative;margin:96px auto 24px;max-height:none!important;overflow:visible!important}.header-player-shell{display:grid;grid-template-columns:1fr 360px;min-height:600px}.header-player-stage{padding:40px}.header-player-cover{width:300px;height:300px;background:#888}.header-player-track{height:80px}.header-player-playlist{overflow:auto;max-height:300px}.header-player-panel[hidden]{display:none}'''
html='''<!doctype html><html data-leskov-app data-theme="light"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><main style="height:1600px">Site</main><div id="headerPlayerPanel" class="header-player-panel" hidden><div class="header-player-backdrop"></div><div class="header-player-dialog"><div class="header-player-shell"><section class="header-player-stage"><div class="header-player-now"><div class="header-player-cover"></div><strong data-header-title>Long track title</strong></div></section><section class="header-player-playlist">'''+''.join(f'<div class="header-player-track">Track {i}</div>' for i in range(20))+'''</section></div></div></div></body></html>'''
with sync_playwright() as pw:
    browser=pw.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'),args=['--no-sandbox','--disable-dev-shm-usage'])
    p=browser.new_page()
    count=0
    for w,h in [(320,560),(360,700),(412,820),(640,360)]:
        for theme in ['light','dark']:
            for app_first in [True,False]:
                p.set_viewport_size({'width':w,'height':h});p.set_content(html)
                p.evaluate('(t)=>document.documentElement.dataset.theme=t',theme)
                if app_first:p.add_style_tag(path=root/'app/src/main/assets/leskov-mobile.css')
                p.add_style_tag(content=site)
                for f in os.environ.get('LESKOV_SITE_CSS','').split(os.pathsep):
                    if f:p.add_style_tag(path=f)
                if not app_first:p.add_style_tag(path=root/'app/src/main/assets/leskov-mobile.css')
                p.evaluate('document.querySelector("#headerPlayerPanel").hidden=false')
                for scroll in [False,True]:
                    if scroll:p.locator('.header-player-dialog').evaluate('(e)=>e.scrollTop=e.scrollHeight')
                    assert p.locator('.header-player-backdrop').evaluate('(e)=>{let r=e.getBoundingClientRect();return Math.abs(r.top)<1 && Math.abs(r.left)<1 && Math.abs(r.bottom-innerHeight)<2 && Math.abs(r.right-innerWidth)<2}')
                    assert p.locator('.header-player-dialog').evaluate('(e)=>e.scrollHeight>e.clientHeight && e.scrollWidth<=e.clientWidth+1')
                assert p.locator('.header-player-track').last.evaluate('(e)=>e.getBoundingClientRect().bottom<=innerHeight+1'), (w,h,theme,app_first,p.evaluate('''()=>[...document.querySelectorAll('.header-player-dialog,.header-player-shell,.header-player-playlist,.header-player-track:last-child')].map(e=>[e.className,e.scrollTop,e.scrollHeight,e.clientHeight,e.getBoundingClientRect().bottom,getComputedStyle(e).overflowY,getComputedStyle(e).scrollBehavior])'''))
                p.evaluate('document.querySelector("#headerPlayerPanel").hidden=true')
                assert p.locator('#headerPlayerPanel').is_hidden()
                count+=1
    browser.close();print(f'PASS {count} Premium viewport scenarios (theme, CSS order, scroll, close)')
