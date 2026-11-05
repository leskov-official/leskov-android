"""Isolated PHP endpoint tests; no production sessions or database writes."""
import json, pathlib, subprocess, tempfile
root = pathlib.Path(__file__).resolve().parents[1]
stub = '''<?php
function cms_send_security_headers() {}
function app_current_user(){return getenv('CASE')==='guest'?null:['id'=>7];}
function cms_verify_csrf(){if(($_POST['csrf_token']??'')!=='fixture'){http_response_code(403);echo '{"ok":false}';exit;}}
function cms_rate_limit(...$args){}
function app_require_premium_track_action($id){if(getenv('CASE')==='nonpremium')throw new Exception('Premium required');}
function app_track_source_row(...$args){return getenv('CASE')==='missing'?null:['audio_path'=>'/song.mp3','title'=>'Song','owner_name'=>'Artist'];}
function app_toggle_track_like(...$args){$GLOBALS['mutations']++;}
function app_save_track_to_library(...$args){$GLOBALS['mutations']++;}
function db_value(...$args){return 1;}
function app_track_user_liked(...$args){return true;}
function app_track_likes_count(...$args){return 12;}
function app_media_url($path){return 'https://lesv.online'.$path;}
'''
with tempfile.TemporaryDirectory() as directory:
    temp=pathlib.Path(directory);(temp/'includes').mkdir();(temp/'includes/app.php').write_text(stub)
    (temp/'mobile_tracks.php').write_bytes((root/'backend/mobile_tracks.php').read_bytes())
    for case,status,mutations in [('guest',401,0),('get',405,0),('csrf',403,0),('invalid',400,0),('nonpremium',400,0),('missing',404,0),('like',200,1),('save',200,1)]:
        wrapper='''<?php
http_response_code(200);$GLOBALS['mutations']=0;
$_SERVER['REQUEST_METHOD']='''+repr('GET' if case=='get' else 'POST')+''';
$_POST=['csrf_token'=>'''+repr('bad' if case=='csrf' else 'fixture')+''','source_type'=>'catalog','source_id'=>7,'action'=>'''+repr('bad' if case=='invalid' else 'save_track_to_library' if case=='save' else 'toggle_track_like')+'''];
register_shutdown_function(function(){fwrite(STDERR,json_encode([http_response_code(),$GLOBALS['mutations']]));});
require __DIR__.'/mobile_tracks.php';'''
        (temp/'run.php').write_text(wrapper)
        import os
        result=subprocess.run(['php',str(temp/'run.php')],env={**os.environ,'CASE':case},capture_output=True,text=True,check=True)
        assert json.loads(result.stderr)==[status,mutations],(case,result.stderr)
        response=json.loads(result.stdout);assert response['ok']==(status==200),(case,response)
        if status==200:assert response['likes']==12 and response['track']['src']=='https://lesv.online/song.mp3'
        print('PASS endpoint',case,status,'mutations',mutations)
helper=str(root/'backend/includes/mobile_push.php')
for url,target in [('/feed.php#post-8','https://lesv.online/feed.php#post-8'),('https://lesv.online/messages.php?user=42','https://lesv.online/messages.php?user=42'),('https://evil.test/a','https://lesv.online/notifications.php'),('//evil.test/a','https://lesv.online/notifications.php')]:
    script='require '+json.dumps(helper)+'; echo app_mobile_push_target_url('+json.dumps(url)+');'
    assert subprocess.check_output(['php','-r',script],text=True)==target
print('PASS push target normalization')
