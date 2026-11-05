(function () {
    'use strict';
    if (location.origin !== 'https://lesv.online' || window.top !== window || window.__leskovAppShell) return;
    var scheduled = false, previous = '', pushText = '';
    function callActive() {
        return ['[data-call-incoming]:not([hidden])','[data-call-active]:not([hidden])',
            '#incomingCallModal:not([hidden])','#callOverlay:not([hidden])'].some(function(selector) {
                var el = document.querySelector(selector);
                return el && getComputedStyle(el).display !== 'none';
            });
    }
    function publish() {
        if (!document.body) return;
        var lang = document.documentElement.lang || 'ru';
        var uid = parseInt(document.body.getAttribute('data-current-user-id'),10);
        var payload = JSON.stringify({url:location.href,language:lang,
            userId:Number.isFinite(uid) ? uid : -1,callActive:callActive()});
        if (payload !== previous && window.LeskovPageBridge) {
            previous=payload; window.LeskovPageBridge.postMessage(payload);
        }
    }
    function pushControls() {
        if (!window.LeskovNativePush) return;
        // The website disables Web Push in WebView. These controls use Android FCM instead.
        document.querySelectorAll('[data-notifications-push-enable]').forEach(function(button) {
            if (button.disabled) button.disabled=false;
        });
        var lang=(document.documentElement.lang || 'ru').slice(0,2);
        var text=pushText || ({ru:'Нажмите кнопку, чтобы подключить уведомления приложения.',
            et:'Rakenduse teavituste seadistamiseks vajuta nuppu.',
            en:'Tap the button to connect app notifications.'}[lang] || 'Tap to connect app notifications.');
        document.querySelectorAll('[data-notifications-push-status]').forEach(function(el) {
            if (el.textContent!==text) el.textContent=text;
        });
    }
    function refresh() {
        scheduled=false;
        if (!document.documentElement || !document.head) return;
        document.documentElement.setAttribute('data-leskov-app','');
        var viewport=document.querySelector('meta[name="viewport"]');
        if (!viewport) {viewport=document.createElement('meta');viewport.name='viewport';document.head.appendChild(viewport);}
        if (viewport.content!=='width=device-width, initial-scale=1, viewport-fit=cover') {
            viewport.content='width=device-width, initial-scale=1, viewport-fit=cover';
        }
        var badge=document.getElementById('leskov-native-playback-badge');
        if (badge) badge.remove();
        pushControls(); publish();
    }
    function schedule() {
        if (!scheduled) { scheduled=true; requestAnimationFrame(refresh); }
    }
    window.__leskovAppShell = {
        navigate:function(raw) {
            var url;
            try {url=new URL(raw,location.href);} catch (_) {return 'hard';}
            if (url.origin!==location.origin || url.username || url.password) return 'hard';
            if (callActive()) return 'call_active';
            if (typeof window.__leskovNavigateWithPlayer==='function' && window.__leskovNavigateWithPlayer(url.href)) return 'soft';
            return 'hard';
        },
        refresh:refresh,
        setPushStatus:function(text) {pushText=String(text || '');pushControls();}
    };
    document.addEventListener('leskov:soft-navigation',function() {refresh();});
    window.addEventListener('popstate',schedule);
    document.addEventListener('click',function(e) {
        var button=e.target && e.target.closest && e.target.closest('[data-notifications-push-enable]');
        if (button && window.LeskovNativePush) {
            e.preventDefault();e.stopImmediatePropagation();window.LeskovNativePush.postMessage('request');
        }
    },true);
    var observer=new MutationObserver(schedule);
    observer.observe(document,{subtree:true,childList:true,attributes:true,
        attributeFilter:['hidden','class','disabled','lang','data-current-user-id']});
    document.addEventListener('DOMContentLoaded',refresh,{once:true});
    refresh();
})();
