(function(){
    'use strict';
    if(location.origin!=='https://lesv.online' || window.top!==window || window.__leskovTrackActions) return;
    function field(form,name){var input=form.querySelector('[name="'+name+'"]');return input ? input.value : '';}
    function labels(){return ({ru:{like:'Лайк',saved:'✓ Добавлено',failed:'Не удалось сохранить изменение. Повторите попытку.',login:'Войдите в аккаунт, чтобы продолжить.'},
        et:{like:'Meeldib',saved:'✓ Lisatud',failed:'Muudatust ei salvestatud. Proovi uuesti.',login:'Jätkamiseks logi sisse.'},
        en:{like:'Like',saved:'✓ Added',failed:'Could not save the change. Try again.',login:'Sign in to continue.'}})[(document.documentElement.lang||'ru').slice(0,2)] || {like:'Like',saved:'✓ Added',failed:'Try again.',login:'Sign in to continue.'};}
    function notify(text){
        if(window.LeskovPageBridge) window.LeskovPageBridge.postMessage(JSON.stringify({type:'notice',message:text}));
    }
    function apply(state){
        document.querySelectorAll('form').forEach(function(form){
            if(field(form,'source_type')!==state.source_type || Number(field(form,'source_id'))!==Number(state.source_id)) return;
            var action=field(form,'action'),button=form.querySelector('button[type="submit"],button:not([type])');
            if(!button) return;
            if(action==='toggle_track_like'){
                button.classList.toggle('is-active',!!state.liked);
                button.setAttribute('aria-pressed',String(!!state.liked));
                button.textContent=(state.liked?'♥ ':'♡ ')+labels().like;
                var card=form.closest('[data-track-card],.track-card');
                if(card) card.querySelectorAll('.track-social-meta span').forEach(function(el){
                    if(/^♥/.test(el.textContent.trim())) el.textContent='♥ '+state.likes;
                });
            } else if(action==='save_track_to_library' && state.saved){
                form.dataset.leskovSaved='1';button.disabled=true;button.textContent=labels().saved;
            }
        });
    }
    function addToQueue(track){
        var manager=window.__leskovPlayerManager;
        if(manager && manager.instances) manager.instances.forEach(function(p){
            if(!p || !p.isPremium || !Array.isArray(p.playlist)) return;
            var found=p.playlist.some(function(item){try{return new URL(item.src,document.baseURI).href===new URL(track.src,document.baseURI).href;}catch(_){return false;}});
            if(!found){
                var queue=p.playlist.concat([track]);
                if(typeof p.setPlaylist==='function') p.setPlaylist(queue,p.playlistIndex);
                else p.playlist=queue;
            }
        });
        if(typeof window.__lsvRefreshMusic==='function') window.__lsvRefreshMusic();
    }
    document.addEventListener('submit',function(e){
        var form=e.target;
        if(!form || !form.querySelector) return;
        var action=field(form,'action');
        if(action!=='toggle_track_like' && action!=='save_track_to_library') return;
        e.preventDefault();e.stopImmediatePropagation();
        if(form.dataset.leskovBusy==='1' || form.dataset.leskovSaved==='1') return;
        form.dataset.leskovBusy='1';
        var button=form.querySelector('button[type="submit"],button:not([type])');
        if(button){button.disabled=true;button.setAttribute('aria-busy','true');}
        fetch('/mobile_tracks.php',{method:'POST',credentials:'same-origin',redirect:'error',
            headers:{'X-Requested-With':'XMLHttpRequest'},body:new FormData(form)})
            .then(async function(response){
                var state=await response.json();
                if(!response.ok || !state.ok) throw new Error(response.status===401?'auth_required':'action_failed');
                apply(state);
                if(action==='save_track_to_library' && state.saved && state.track) addToQueue(state.track);
                document.dispatchEvent(new CustomEvent('leskov:track-updated',{detail:state}));
            }).catch(function(error){notify(error.message==='auth_required'?labels().login:labels().failed);})
            .finally(function(){
                delete form.dataset.leskovBusy;
                if(button){button.removeAttribute('aria-busy');button.disabled=form.dataset.leskovSaved==='1';}
            });
    },true);
    window.__leskovTrackActions={apply:apply};
})();
