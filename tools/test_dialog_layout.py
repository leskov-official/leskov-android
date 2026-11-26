#!/usr/bin/env python3
"""Offline Chromium regression for the PHP messenger DOM contract. No account or network."""
from pathlib import Path
import os
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
# Representative conflicting rules from the mobile site. Optional full site CSS for local integration.
SITE = '''.messenger-layout {display:grid;grid-template-columns:320px 1fr;width:calc(100vw - 48px);margin:32px auto}
.messenger-sidebar {position:sticky;max-height:calc(100vh - 48px);overflow:auto;display:grid;gap:14px}
.messenger-main {display:grid;min-height:calc(100vh - 150px);gap:22px}
html.lsv-winter-ice body.messages-page .messenger-layout{display:grid!important;grid-template-columns:1fr!important;align-items:start!important}
html.lsv-winter-ice body.messages-page .messenger-layout > .messenger-sidebar{height:auto!important;overflow:visible!important}
.user-list-item{display:flex;gap:8px;padding:12px;min-height:50px}.messenger-head {display:flex;flex-wrap:wrap}
.message-bubble{padding:12px}.compose-box textarea{width:100%}.sidebar-headline{display:flex;justify-content:space-between}'''

def markup(selected, winter, friends=False):
    bio = 'Место, где Ваш контент звучит без шума алгоритмов. ' * 14
    avatar = '<span class="avatar-stack"><img alt="" src="data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%2744%27 height=%2744%27/%3E"><span class="online-dot"></span></span>'
    details = '<span class="messenger-user-copy"><strong class="inline-badged-name"><span class="user-list-name">LESKOV</span><span class="user-list-badges">✓ ★ ◉</span></strong><p class="messenger-peer-presence">Сейчас в сети</p><p class="messenger-peer-bio">'+bio+'</p></span>'
    rows = ''.join('<a class="user-list-item" href="?user=42">'+avatar+details+'</a>' for i in range(60))
    thread = ('<div class="messenger-head"><div class="user-list-item is-active">'+avatar+details+'<div class="messenger-head-actions"><a class="site-btn">Секретный чат</a><button class="site-btn">Видеозвонок</button><form><button class="site-btn">Удалить чат</button></form></div><a class="lsv-peer-hint">Профиль ›</a></div></div>'
              '<div id="messageThread" class="message-thread">'+ '<p class="message-bubble">Сообщение</p>'*60 + '</div>'
              '<form id="messageComposer" class="compose-box" data-live-message-send><div class="lsv-compose-input-shell"><textarea id="messageInput" name="message"></textarea><div class="lsv-compose-inline-actions"><button type="button" class="site-btn">Mic</button><button type="button" class="site-btn">Video</button><button class="site-btn" type="submit">Send</button></div></div><div class="compose-tools"><label class="site-btn">Загрузить файл<input type="file" name="message_image" id="messageFileInput" hidden></label><label class="site-btn">Фото<input type="file" name="message_images[]" id="messageImagesInput" accept="image/*" multiple hidden></label><button type="button" class="site-btn">Стикеры</button><button type="button" class="site-btn">Подарки</button></div></form>') if selected else '<p>Выберите диалог</p>'
    return f'''<!doctype html><html lang="ru" class="{'lsv-winter-ice lsv-season-on' if winter else ''}"><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body class="app-shell messages-page" data-current-user-id="7" data-active-user="{42 if selected else 0}">{'<div class="lsv-season-garland"></div>' if winter else ''}<div class="messenger-layout"><aside class="messenger-sidebar surface-card"><a>Профиль</a><a>Секретные переписки</a><input id="messengerSearch"><div class="sidebar-headline"><h1>Диалоги</h1></div><div data-messenger-dialogs style="{'display:none' if friends else ''}">{rows}</div><div data-messenger-friends style="{'display:block' if friends else 'display:none'}">{rows}</div></aside><main class="messenger-main surface-card">{thread}</main></div></body></html>'''

with sync_playwright() as pw:
    browser = pw.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'), args=['--no-sandbox','--disable-dev-shm-usage'])
    page = browser.new_page()
    def setup(selected,winter,friends=False):
        page.goto('about:blank')
        page.set_content(markup(selected,winter,friends))
        # Production WebView may inject its CSS before the site's stylesheets.
        if os.environ.get('APP_CSS_FIRST') == '1': page.add_style_tag(path=ASSETS/'leskov-mobile.css')
        page.add_style_tag(content=SITE)
        for file in os.environ.get('LESKOV_SITE_CSS','').split(os.pathsep):
            if file: page.add_style_tag(path=file)
        if os.environ.get('APP_CSS_FIRST') != '1': page.add_style_tag(path=ASSETS/'leskov-mobile.css')
        # Inject a fixture origin only into the test script, never into shipped assets.
        page.add_script_tag(content='(function(location){'+(ASSETS/'leskov-app-shell.js').read_text()+'})(new URL("https://lesv.online/messages.php"));')
    count=0
    for width in (320,360,412):
        for winter in (False,True):
            page.set_viewport_size({'width':width,'height':720})
            for friends in (False,True):
                setup(False,winter,friends)
                assert page.locator('.messenger-main').is_hidden()
                if winter:
                    assert page.locator('.messenger-layout').evaluate('(e)=>e.getBoundingClientRect().top>=56')
                assert page.locator('.messenger-sidebar').evaluate('(e)=>{e.scrollTop=e.scrollHeight;return e.scrollTop>0 && e.scrollHeight>e.clientHeight}')
                target='[data-messenger-friends]' if friends else '[data-messenger-dialogs]'
                assert page.locator(target+' a').last.evaluate('(e)=>e.getBoundingClientRect().bottom<=innerHeight+1')
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                count+=1
            setup(True,winter)
            assert page.locator('.messenger-sidebar').is_hidden()
            assert page.locator('#leskov-dialogs-back').count()==1
            assert page.locator('.messenger-head .messenger-peer-bio').evaluate("e=>getComputedStyle(e).whiteSpace==='nowrap' && e.clientHeight<=22 && e.scrollWidth>e.clientWidth")
            assert page.locator('#messageThread').evaluate('(e)=>e.clientHeight>=innerHeight*0.46'), page.locator('#messageThread').bounding_box()
            assert page.locator('#messageThread').evaluate('(e)=>e.getBoundingClientRect().width>=innerWidth-32')
            assert page.locator('.compose-tools').evaluate('(e)=>e.getBoundingClientRect().bottom<=innerHeight+1')
            photo=page.locator('#messageImagesInput')
            for n in (1,4):
                photo.set_input_files([{'name':f'photo-{i}.png','mimeType':'image/png','buffer':b'fixture'} for i in range(n)])
                assert page.locator('#leskov-photo-selection').inner_text().startswith(f'Фото выбрано: {n}')
                assert page.locator('#messageComposer').evaluate("e=>new FormData(e).getAll('message_images[]').filter(f=>f.size>0).length")==n
                page.locator('#leskov-photo-selection button').click()
                assert page.locator('#leskov-photo-selection').count()==0
            page.locator('#messageFileInput').set_input_files({'name':'test.pdf','mimeType':'application/pdf','buffer':b'fixture'})
            assert page.locator('#messageComposer').evaluate("e=>new FormData(e).get('message_image').name")=='test.pdf'
            page.locator('#messageFileInput').set_input_files([])
            assert page.locator('#messageThread').evaluate('(e)=>{e.scrollTop=100;window.__leskovAppShell.refresh();return e.scrollTop===100 && e.clientHeight>=96}')
            for height in (720,340):
                page.set_viewport_size({'width':width,'height':height})
                page.locator('#messageInput').scroll_into_view_if_needed()
                assert page.locator('#messageInput').evaluate('(e)=>{const r=e.getBoundingClientRect();return r.top>=-1 && r.bottom<=innerHeight+1}')
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                count+=1
    browser.close()
    print(f'PASS {count} dialog / friends / conversation viewport scenarios')
