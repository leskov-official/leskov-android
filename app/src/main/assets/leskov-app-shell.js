(function () {
    'use strict';
    if (location.origin !== 'https://lesv.online' || window.top !== window || window.__leskovAppShell) return;
    var logoutBusy = false, scheduled = false, previous = '', pushText = '', pushState = {enabled:false,status:'unknown'}, sessionUserId=null, sessionRequested=false;
    function callActive() {
        return ['[data-call-incoming]:not([hidden])','[data-call-active]:not([hidden])',
            '#incomingCallModal:not([hidden])','#callOverlay:not([hidden])'].some(function(selector) {
                var el = document.querySelector(selector);
                return el && getComputedStyle(el).display !== 'none';
            });
    }
    function publish() {
        if (!document.body || logoutBusy) return;
        var lang = document.documentElement.lang || 'ru';
        var uid = parseInt(document.body.getAttribute('data-current-user-id'),10);
        if (!Number.isFinite(uid) && sessionUserId!==null) uid=sessionUserId;
        var payload = JSON.stringify({url:location.href,language:lang,
            userId:Number.isFinite(uid) ? uid : -1,callActive:callActive(),lightTheme:lightTheme()});
        if (payload !== previous && window.LeskovPageBridge) {
            previous=payload; window.LeskovPageBridge.postMessage(payload);
        }
    }
    function lightTheme() {
        var root=document.documentElement;
        var theme=root.getAttribute ? root.getAttribute('data-theme') : null;
        return theme==='light' || (theme==='auto' && typeof window.matchMedia==='function' && window.matchMedia('(prefers-color-scheme: light)').matches);
    }
    function pushControls() {
        if (!window.LeskovNativePush) return;
        // The website disables Web Push in WebView. These controls use Android FCM instead.
        var lang=(document.documentElement.lang || 'ru').slice(0,2);
        document.querySelectorAll('[data-notifications-push-enable]').forEach(function(button) {
            var busy=pushState.status==='connecting';
            if (button.disabled!==busy) button.disabled=busy;
            var labels=pushState.enabled ? {ru:'✓ Push-уведомления включены',et:'✓ Tõuketeavitused on lubatud',en:'✓ Push notifications enabled'} :
                {ru:'Включить Push на этом устройстве',et:'Luba tõuketeavitused selles seadmes',en:'Enable Push on this device'};
            var label=labels[lang] || labels.en;
            if (button.textContent!==label) button.textContent=label;
            if (button.getAttribute('aria-pressed')!==String(!!pushState.enabled)) button.setAttribute('aria-pressed',String(!!pushState.enabled));
            button.classList.toggle('is-enabled',!!pushState.enabled);
        });
        var text=pushText || ({ru:'Нажмите кнопку, чтобы подключить уведомления приложения.',
            et:'Rakenduse teavituste seadistamiseks vajuta nuppu.',
            en:'Tap the button to connect app notifications.'}[lang] || 'Tap to connect app notifications.');
        document.querySelectorAll('[data-notifications-push-status]').forEach(function(el) {
            if (el.textContent!==text) el.textContent=text;
        });
    }
    function session() {
        if (logoutBusy || sessionRequested || !document.body || document.body.hasAttribute('data-current-user-id') || typeof fetch!=='function') return;
        sessionRequested=true;
        fetch('/mobile_push.php?action=csrf',{credentials:'same-origin',cache:'no-store'})
            .then(function(response){if(response.status===401)return {user_id:0};return response.json();})
            .then(function(state){if(Number.isInteger(state.user_id)){sessionUserId=state.user_id;publish();}})
            .catch(function(){sessionRequested=false;});
    }
    function useSiteMessengerLayout() {
        var root=document.documentElement;
        var nativeSite=location.pathname === '/messages.php' || (!!document.body && document.body.classList.contains('messages-page'));
        if (nativeSite) root.removeAttribute('data-leskov-app');
        else if (!root.hasAttribute || !root.hasAttribute('data-leskov-app')) root.setAttribute('data-leskov-app','');
    }
    function refresh() {
        scheduled=false;
        if (!document.documentElement || !document.head) return;
        useSiteMessengerLayout();
        var viewport=document.querySelector('meta[name="viewport"]');
        if (!viewport) {viewport=document.createElement('meta');viewport.name='viewport';document.head.appendChild(viewport);}
        if (viewport.content!=='width=device-width, initial-scale=1, viewport-fit=cover') {
            viewport.content='width=device-width, initial-scale=1, viewport-fit=cover';
        }
        var badge=document.getElementById('leskov-native-playback-badge');
        if (badge) badge.remove();
        pushControls(); publish(); session();
    }
    function schedule() {
        if (!scheduled) { scheduled=true; requestAnimationFrame(refresh); }
    }
    async function performLogout(id) {
        var confirmed=false, controller=new AbortController();
        var timer=setTimeout(function(){controller.abort();},11000);
        var opts={credentials:'same-origin',cache:'no-store',signal:controller.signal};
        try {
            var csrfReply=await fetch('/mobile_push.php?action=csrf',opts);
            if (csrfReply.status===401) {
                // No CSRF means no authenticated push session; do not claim a logout POST succeeded.
                confirmed=false;
            }
            else if (csrfReply.ok) {
                var csrf=await csrfReply.json();
                if (!csrf.ok || typeof csrf.csrf!=='string' || !csrf.csrf) throw new Error('csrf');
                var lang=(document.documentElement.lang||'ru').slice(0,2);
                var body=new URLSearchParams({action:'logout',csrf_token:csrf.csrf});
                var reply=await fetch('/auth.php?lang='+encodeURIComponent(lang),Object.assign({},opts,{method:'POST',body:body}));
                if (!reply.ok) throw new Error('logout');
                var probe=await fetch('/mobile_push.php?action=csrf',opts);
                confirmed=probe.status===401 && (await probe.json()).error==='auth_required';
            }
        } catch (_) { /* no success is reported on network or server failure */ }
        finally {
            clearTimeout(timer);
            if (!confirmed) {logoutBusy=false;sessionRequested=false;}
            if (window.LeskovPageBridge) window.LeskovPageBridge.postMessage(JSON.stringify({type:'logoutResult',id:id,confirmed:confirmed}));
        }
    }
    window.__leskovAppShell = {
        logout:function(id) {
            if (logoutBusy || callActive() || !window.LeskovPageBridge) return false;
            logoutBusy=true;performLogout(id);return true;
        },
        navigate:function(raw) {
            var url;
            try {url=new URL(raw,location.href);} catch (_) {return 'hard';}
            if (url.origin!==location.origin || url.username || url.password) return 'hard';
            if (logoutBusy || callActive()) return 'call_active';
            if (typeof window.__leskovNavigateWithPlayer==='function' && window.__leskovNavigateWithPlayer(url.href)) return 'soft';
            return 'hard';
        },
        refresh:refresh,
        setPushStatus:function(state) {
            if (typeof state==='string') {pushText=state;pushState={enabled:false,status:'unknown'};}
            else {pushState=state || {enabled:false,status:'unknown'};pushText=String(pushState.message || '');}
            pushControls();
        }
    };
    document.addEventListener('leskov:soft-navigation',function() {refresh();});
    window.addEventListener('popstate',schedule);
    if (typeof window.matchMedia==='function') {var mq=window.matchMedia('(prefers-color-scheme: light)'); if(mq.addEventListener) mq.addEventListener('change',schedule);}
    document.addEventListener('click',function(e) {
        var button=e.target && e.target.closest && e.target.closest('[data-notifications-push-enable]');
        if (button && window.LeskovNativePush) {
            e.preventDefault();e.stopImmediatePropagation();window.LeskovNativePush.postMessage(pushState.enabled ? 'settings' : 'request');
        }
    },true);
    var observer=new MutationObserver(schedule);
    observer.observe(document,{subtree:true,childList:true,attributes:true,
        attributeFilter:['hidden','class','disabled','lang','data-current-user-id','data-active-user','data-theme']});
    document.addEventListener('DOMContentLoaded',refresh,{once:true});
    refresh();
})();
