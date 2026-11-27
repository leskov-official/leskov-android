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

# The previous test required a native two-pane replacement. That contradicted the
# requested browser layout. Compare identical markup with/without injected shell.
with sync_playwright() as pw:
    browser = pw.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'), args=['--no-sandbox','--disable-dev-shm-usage'])
    page = browser.new_page()
    count=0
    for width in (320,360,412,768):
        for winter in (False,True):
            for selected in (False,True):
                for theme in ('light','dark'):
                    page.set_viewport_size({'width':width,'height':720})
                    page.goto('about:blank')
                    page.set_content(markup(selected,winter).replace('</body>','<button class="profile-player-fab">▶</button><button class="lsv-season-controls">❄</button></body>'))
                    page.evaluate('(theme)=>document.documentElement.dataset.theme=theme',theme)
                    page.add_style_tag(content=SITE + '@media(max-width:850px){.messenger-layout{display:grid;grid-template-columns:1fr}.profile-player-fab{position:fixed;bottom:40px;right:12px}.lsv-season-controls{position:fixed;bottom:90px;left:12px}}')
                    for file in os.environ.get('LESKOV_SITE_CSS','').split(os.pathsep):
                        if file: page.add_style_tag(path=file)
                    geometry="""()=>[...document.querySelectorAll('.messenger-layout,.messenger-sidebar,.messenger-main,.user-list-item,.messenger-head,#messageThread,#messageComposer,.profile-player-fab,.lsv-season-controls,.lsv-season-garland')].map(e=>{let r=e.getBoundingClientRect(),s=getComputedStyle(e);return [r.x,r.y,r.width,r.height,s.display,s.position,s.overflowY,s.fontSize]})"""
                    baseline=page.evaluate(geometry)
                    page.evaluate("document.documentElement.setAttribute('data-leskov-app','')")
                    style=page.add_style_tag(path=ASSETS/'leskov-mobile.css')
                    if os.environ.get('APP_CSS_FIRST')=='1': style.evaluate('(e)=>document.head.prepend(e)')
                    page.add_script_tag(content='(function(location){'+(ASSETS/'leskov-app-shell.js').read_text()+'})(window.__fixtureLocation=new URL("https://lesv.online/messages.php"));')
                    assert not page.evaluate("document.documentElement.hasAttribute('data-leskov-app')")
                    assert page.evaluate(geometry)==baseline,(width,winter,selected,theme)
                    assert page.locator('#leskov-dialogs-back').count()==0
                    # Subsequent theme/DOM refreshes cannot accidentally re-enable overrides.
                    page.evaluate('window.__leskovAppShell.refresh()')
                    assert page.evaluate(geometry)==baseline
                    if selected:
                        photo=page.locator('#messageImagesInput')
                        photo.set_input_files([{'name':f'photo-{i}.png','mimeType':'image/png','buffer':b'fixture'} for i in range(3)])
                        assert photo.evaluate('(e)=>new FormData(e.form).getAll(e.name).length')==3
                    # A different page still receives the app's original chrome styles.
                    page.evaluate("window.__fixtureLocation.pathname='/profile.php';document.body.classList.remove('messages-page');window.__leskovAppShell.refresh()")
                    assert page.evaluate("document.documentElement.hasAttribute('data-leskov-app')")
                    count+=1
    browser.close();print(f'PASS {count} messenger browser-parity scenarios; native shell does not change site geometry')
