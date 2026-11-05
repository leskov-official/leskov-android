(function () {
    'use strict';
    if (location.origin !== 'https://lesv.online' || window.top !== window || window.__leskovAndroidAudio) return;

    // The site's manager owns Premium playlists, shuffle/repeat and its audio element.
    // Its media may be detached from the DOM; document.querySelector('audio') misses it.
    var last = null;
    var bound = new WeakSet();
    var seen = new WeakSet();
    var timer = null;
    var previous = '';
    var playerSelector = '.leskov-player, .premium-player, [data-premium-player], #premiumPlayerModal, #headerPlayerPanel, [data-header-player], [data-promo-player]';
    function manager() { return window.__leskovPlayerManager; }
    function eligible(a) {
        if (!a || a.tagName !== 'AUDIO' || a.srcObject) return false;
        var raw = (a.currentSrc || a.getAttribute('src') || '').trim();
        if (!raw) return false;
        try {
            var u = new URL(raw, document.baseURI);
            return (u.origin === location.origin || (u.protocol === 'blob:' && u.origin === location.origin)) &&
                !u.username && !u.password && !/(?:call[_-]?ringtone|notification[_-]?(?:sound|chime))/i.test(u.pathname);
        } catch (_) { return false; }
    }
    // The header Premium panel is a second site engine on pages without LeskovPlayer.
    // Invoke its buttons so its pause intent, persistence and playlist mode remain authoritative.
    function headerPlayer(a) {
        var root = document.querySelector('[data-header-player]');
        if (!root || (root.querySelector('audio') !== a && window.__leskovHeaderPlayerAudio !== a)) return null;
        var panel = document.getElementById('headerPlayerPanel') || root;
        var tracks = Array.from(panel.querySelectorAll('[data-header-player-track]'));
        function text(selector) { var el=root.querySelector(selector);return el ? el.textContent.trim() : ''; }
        function click(selector) {
            var button=root.querySelector(selector);
            if (!button || button.disabled) return false;
            button.click(); return true;
        }
        return {media:a, element:root, title:text('[data-header-title]'), artist:text('[data-header-artist]'),
            cover:(root.querySelector('[data-header-cover]') || {}).src || '',
            mode:root.dataset.playerMode || panel.dataset.playerMode || 'sequential',
            playlist:tracks.map(function(el) { return {src:el.dataset.audio,title:el.dataset.title,
                artist:el.dataset.artist,cover:el.dataset.cover}; }),
            playlistIndex:tracks.findIndex(function(el) {
                try { return new URL(el.dataset.audio,document.baseURI).href === (a.currentSrc || a.src); }
                catch (_) { return false; }
            }),
            play:function() { if (a.paused && !click('[data-header-play]')) return a.play(); },
            pause:function() { if (!a.paused && !click('[data-header-play]')) a.pause(); },
            next:function() { click('[data-header-next]'); }, prev:function() { click('[data-header-prev]'); },
            seek:function(seconds) { a.currentTime=seconds; }
        };
    }
    function candidates() {
        var m = manager(), result = [];
        if (m && m.active) result.push({media:m.active.media, player:m.active});
        if (m && m.instances && typeof m.instances.forEach === 'function') {
            m.instances.forEach(function(p) { if (p) result.push({media:p.media, player:p}); });
        }
        var audios = Array.from(document.querySelectorAll('audio'));
        if (window.__leskovHeaderPlayerAudio) audios.push(window.__leskovHeaderPlayerAudio);
        if (window.__leskovPromoPlayerAudio) audios.push(window.__leskovPromoPlayerAudio);
        audios.forEach(function(a) {
            if (!result.some(function(x) { return x.media === a; })) result.push({media:a, player:headerPlayer(a)});
        });
        return result.filter(function(x) { return eligible(x.media); });
    }
    function current() {
        var list = candidates();
        var playing = list.find(function(x) { return !x.media.paused && !x.media.ended; });
        if (playing) last = playing;
        else if (last && !list.some(function(x) { return x.media === last.media; })) last = null;
        // Rebuild adapters after a track/mode change; keep the media identity across pauses.
        if (last) last = list.find(function(x) { return x.media === last.media; }) || null;
        return playing || last;
    }
    function safeArtwork(raw) {
        try {
            var u = new URL(raw, document.baseURI);
            return raw && u.origin === location.origin && !u.username && !u.password ? u.href : '';
        } catch (_) { return ''; }
    }
    function details(item) {
        var a = item.media, p = item.player;
        var node = (p && p.element) || (a.closest && a.closest(playerSelector + ', [data-track-title], .track-card'));
        var meta = navigator.mediaSession && navigator.mediaSession.metadata;
        // Prefer the actual track over stale MediaSession metadata left by another player.
        var title = (p && p.title) || (node && (node.dataset.title || node.dataset.trackTitle)) || (meta && meta.title) || a.title || 'LESKOV';
        var artist = (p && p.artist) || (node && node.dataset.artist) || (meta && meta.artist) || 'Igor Leskov';
        var art = (p && p.cover) || (node && (node.dataset.cover || node.dataset.trackCover)) ||
            (meta && meta.artwork && meta.artwork.length && meta.artwork[meta.artwork.length-1].src) || '';
        var playlist = p && Array.isArray(p.playlist) && p.playlist.length > 1;
        return {playing:!a.paused && !a.ended, title:String(title).slice(0,160), artist:String(artist).slice(0,160),
            artwork:safeArtwork(art), positionMs:Number.isFinite(a.currentTime) ? Math.max(0,Math.round(a.currentTime*1000)) : 0,
            durationMs:Number.isFinite(a.duration) ? Math.max(0,Math.round(a.duration*1000)) : 0,
            hasNext:!!(playlist && typeof p.next === 'function'),
            hasPrevious:!!(playlist && (typeof p.prev === 'function' || typeof p.previous === 'function'))};
    }
    function post(payload) {
        if (window.LeskovAudioBridge) window.LeskovAudioBridge.postMessage(JSON.stringify(payload));
    }
    function report() {
        timer = null;
        var item = current();
        // An idle new page must not overwrite an independent Media3 session.
        if (!item) return;
        var state = details(item), key = JSON.stringify(state);
        if (key !== previous) { previous = key; post(state); }
    }
    function schedule() { if (timer === null) timer = setTimeout(report, 80); }
    function onEvent(e) {
        if (!eligible(e.target) || seen.has(e)) return;
        seen.add(e);
        if (e.type === 'play') post({type:'web_start'});
        schedule();
    }
    var events = ['play','playing','pause','ended','error','loadedmetadata','durationchange','seeked','ratechange'];
    function discover() {
        candidates().forEach(function(item) {
            if (bound.has(item.media)) return;
            bound.add(item.media);
            events.forEach(function(name) { item.media.addEventListener(name, onEvent); });
        });
        report();
        renderNative();
    }
    // Release Media3 before a user-started website track requests Chromium audio focus.
    // Waiting only for the later `play` event can cause a Play -> Pause focus race.
    document.addEventListener('pointerdown',function(e) {
        var button = e.target && e.target.closest && e.target.closest(
            '.leskov-player__btn--play, .leskov-player__center-btn[data-action="play"], [data-playlist-index], [data-header-play], [data-header-player-track], [data-header-next], [data-header-prev]');
        if (!button || button.disabled || button.getAttribute('aria-disabled') === 'true') return;
        var root = button.closest(playerSelector);
        if (!root) return;
        var media = root.querySelector('audio');
        if (eligible(media) || candidates().some(function(item) { return item.player && item.player.element === root; })) {
            post({type:'web_start'});
        }
    },true);
    // Capture catches media inserted and played before the next discovery, including autoplay.
    events.forEach(function(name) { document.addEventListener(name, onEvent, true); });
    ['play','pause','ended','statechange','trackchange','playlistchange'].forEach(function(name) {
        document.addEventListener('leskov:player:' + name, schedule);
    });
    function command(action) {
        var item = current();
        if (!item) return false;
        var a = item.media, p = item.player;
        if (action === 'pause') { if (p && typeof p.pause === 'function') p.pause(); else a.pause(); }
        else if (action === 'play') {
            var promise = p && typeof p.play === 'function' ? p.play() : a.play();
            if (promise && promise.catch) promise.catch(schedule);
        } else if (action === 'next' && p && details(item).hasNext) p.next();
        else if (action === 'previous' && p && details(item).hasPrevious) {
            if (typeof p.previous === 'function') p.previous(); else p.prev();
        } else if (/^seek:\d+$/.test(action) && Number.isFinite(a.duration)) {
            var seconds = Math.min(a.duration, Number(action.slice(5))/1000);
            if (p && typeof p.seek === 'function') p.seek(seconds); else a.currentTime = seconds;
        } else return false;
        schedule();
        return true;
    }
    function snapshot() {
        var item = current();
        if (!item || item.media.paused || item.media.ended) return null;
        var raw = item.media.currentSrc || item.media.getAttribute('src');
        var u = new URL(raw,document.baseURI);
        // Blob/MSE playback stays with the site's player; only authorized HTTPS can be handed off.
        if (u.protocol !== 'https:' || u.origin !== location.origin) return null;
        var state = details(item);
        var result = {url:u.href,title:state.title,artist:state.artist,artwork:state.artwork,position:state.positionMs};
        var p = item.player;
        // Copy only the queue already supplied by the website, with bounded IPC size.
        // Server authorization is still required for each stream URL.
        if (p && Array.isArray(p.playlist) && p.playlist.length <= 100) {
            var queue = p.playlist.map(function(track) {
                try {
                    var url = new URL(track.src,document.baseURI);
                    if (!track.src || url.origin !== location.origin || url.protocol !== 'https:' ||
                        url.username || url.password || url.href.length > 4096 ||
                        /(?:call[_-]?ringtone|notification[_-]?(?:sound|chime))/i.test(url.pathname)) return null;
                    return {url:url.href,title:String(track.title || 'LESKOV').slice(0,160),
                        artist:String(track.artist || 'Igor Leskov').slice(0,160),artwork:safeArtwork(track.cover || '').slice(0,1500)};
                } catch (_) { return null; }
            });
            var index = Number.isInteger(p.playlistIndex) ? p.playlistIndex : -1;
            if (queue.length && queue.every(Boolean) && queue[index] && queue[index].url === u.href) {
                result.queue = queue; result.queueIndex = index;
                result.mode = ['repeat','shuffle'].includes(p.mode) ? p.mode : 'sequential';
            }
        }
        return JSON.stringify(result);
    }
    function renderNative() {
        var state = window.__leskovNativePlayback;
        var badge = document.getElementById('leskov-native-playback-badge');
        if (!state || !state.active) { if (badge) badge.remove(); return; }
        var player = document.querySelector('#headerPlayerPanel [data-header-player]') || document.querySelector(playerSelector);
        if (!player || !window.LeskovNativePlayback) return;
        if (!badge) {
            badge = document.createElement('button');
            badge.type = 'button'; badge.id = 'leskov-native-playback-badge';
            badge.addEventListener('click',function(e) {
                e.preventDefault(); e.stopPropagation(); window.LeskovNativePlayback.postMessage('toggle');
            });
        }
        if (!player.contains(badge)) player.appendChild(badge);
        var text = (state.buffering ? '◌ ' : state.playing ? '⏸ ' : '▶ ') + state.title + ' · ' + state.artist;
        if (badge.textContent !== text) badge.textContent = text;
        badge.setAttribute('aria-pressed',String(state.playing));
        badge.setAttribute('aria-busy',String(!!state.buffering));
    }
    window.__leskovAndroidAudio = {snapshot:snapshot, command:command, report:report, renderNative:renderNative};
    window.addEventListener('leskov:native-playback',renderNative);
    window.addEventListener('pageshow',discover);
    document.addEventListener('visibilitychange',discover);
    // Covers detached Premium media and asynchronously mounted players, without patching site methods.
    setInterval(discover,1500);
    discover();
})();
