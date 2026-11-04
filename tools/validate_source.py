#!/usr/bin/env python3
"""Static checks, not a replacement for Android Gradle or on-device tests."""
from pathlib import Path
import re, subprocess, sys, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
errors=[];passed=[]

def check(name,condition):
 (passed if condition else errors).append(name)

# The build may legitimately inject the REAL Firebase config from GitHub Actions Secrets.
# Reject only placeholders or a config registered for the wrong Android package.
_config = root/'app/google-services.json'
if not _config.exists():
 check('Firebase config optional for debug build', True)
else:
 try:
  import json
  firebase=json.loads(_config.read_text())
  clients=firebase.get('client', [])
  package_names=[c.get('client_info',{}).get('android_client_info',{}).get('package_name') for c in clients]
  keys=[str(k.get('current_key','')) for c in clients for k in c.get('api_key',[])]
  project_number=str(firebase.get('project_info',{}).get('project_number',''))
  check('Real Firebase configuration matches LESKOV applicationId',
        'com.aistudio.leskov.musix' in package_names and
        project_number not in ('', '100000000000') and
        bool(keys) and all('Fake' not in k for k in keys))
 except Exception as e:
  check('Firebase config JSON parse: '+str(type(e).__name__),False)
s=(root/'app/build.gradle.kts').read_text()
check('Google services plugin applied only conditionally', 'if (firebaseConfig.exists())' in s and 'apply(plugin = "com.google.gms.google-services")' in s)
check('Standard debug keystore is used', 'signingConfigs.getByName("debugConfig")' not in s)
check('Target SDK 36 remains', 'targetSdk = 36' in s)
check('Generated R and BuildConfig namespace matches Kotlin sources',
      'namespace = "com.example"' in s and
      'applicationId = "com.aistudio.leskov.musix"' in s)
media=(root/'app/src/main/java/com/example/media/LeskovMediaService.kt').read_text()
check('Unresolved audioManager assignment removed', 'audioManager = getSystemService' not in media)
check('Media notification uses LESKOV monochrome icon', '.setSmallIcon(R.drawable.ic_notification_leskov)' in media)
web=(root/'app/src/main/java/com/example/ui/components/LeskovWebView.kt').read_text()
check('Secure origin-limited WebMessageListener used', 'WebViewCompat.addWebMessageListener' in web)
check('JavascriptInterface not used for audio bridge', 'addJavascriptInterface(' not in web)
check('No invasive getUserMedia monkey-patch', 'origGetUserMedia' not in web)
check('Audio observer handles playing and error events', "document.addEventListener('playing'" in web and "document.addEventListener('error'" in web)
push=(root/'app/src/main/java/com/example/push/FcmTokenManager.kt').read_text()
check('No fabricated token upload endpoint', 'profile.php?ajax=register_fcm_token"' not in push)
check('No unverified HTTP client token uploads', 'httpClient.newCall(' not in push)
call=(root/'app/src/main/java/com/example/push/LeskovFirebaseMessagingService.kt').read_text()
check('Fake call answer button removed', 'addAction(' not in call)
dialog=(root/'app/src/main/java/com/example/ui/components/LeskovClearCacheDialog.kt').read_text()
check('Real site session logout route used', 'auth.php?logout=1' in dialog)
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
check('Default FCM icon configured', 'default_notification_icon' in manifest)
for p in list((root/'app/src/main/res').rglob('*.xml')) + [root/'app/src/main/AndroidManifest.xml']:
 try: ET.parse(p);check(f'XML parse {p.relative_to(root)}',True)
 except Exception as e:check(f'XML parse {p.relative_to(root)}: {e}',False)
for name,content in re.findall(r'val\s+(js\w+)\s*=\s*"""(.*?)"""\.trimIndent\(\)',web,re.S):
 try:
  r=subprocess.run(['node','--check','-'],input=content,capture_output=True,text=True,timeout=10)
  check(f'JS syntax {name}: {r.stderr[:80]}',r.returncode==0)
 except FileNotFoundError:check(f'JS syntax {name} (Node unavailable)',False)
# LESKOV 3.1 regression checks (static evidence; not a claim of Android compilation).
from pathlib import Path as _P
handoff=(root/'app/src/main/java/com/example/media/LeskovAudioHandoff.kt').read_text()
check('Empty audio source rejected before creating Media3 URL', 'if(!raw) return null;' in handoff)
check('WebRTC streams are never transferred as music', 'audio.srcObject' in handoff)
check('Chromium playback is paused before launching Media3',
      handoff.index('webView.evaluateJavascript(pauseJs)') < handoff.index('LeskovNativeAudioService.handoff('))
main=(root/'app/src/main/java/com/example/MainActivity.kt').read_text()
check('System status bar inset protects web content', '.windowInsetsPadding(WindowInsets.statusBars)' in main)
check('Bottom navigation responds to IME', 'keyboardVisible' in main and 'WindowInsets.ime' in main)
check('Guest gate and offline overlays animate', main.count('AnimatedVisibility(') >= 4)
check('Runtime web media grant checks current first-party page', 'isTrustedCurrentPage()' in main)
nav=(root/'app/src/main/java/com/example/ui/components/LeskovBottomBar.kt').read_text()
check('Animated nav selection + language transitions', 'Crossfade(' in nav and 'animateDpAsState(' in nav)
auth=(root/'app/src/main/java/com/example/ui/components/LeskovAuthGate.kt').read_text()
check('Animated auth button feedback', 'collectIsPressedAsState' in auth and '.scale(loginScale)' in auth)
mini=(root/'app/src/main/java/com/example/ui/components/LeskovMiniPlayer.kt').read_text()
check('Media buffering feedback rendered', 'state.buffering' in mini and 'LinearProgressIndicator(' in mini)
check('Hidden popup WebView no longer leaks', 'onCreateWindow(' not in web and 'setSupportMultipleWindows(false)' in web)
check('FCM backend integration contract accompanies Android sources',
      (root/'docs/BACKEND_FCM_CONTRACT.md').is_file())
try:
 import tomllib
 with open(root/'gradle/libs.versions.toml','rb') as _f: tomllib.load(_f)
 check('Version catalog TOML parses',True)
except Exception as _e:check('Version catalog TOML parses: '+str(_e),False)
try:
 import yaml
 with open(root/'.github/workflows/android.yml') as _f: _flow=yaml.safe_load(_f)
 check('GitHub Actions workflow parses', 'jobs' in _flow)
except Exception as _e:check('GitHub Actions workflow parses: '+str(_e),False)
try:
 _result=subprocess.run(['node',str(root/'tools/test_audio_snapshot.js')],capture_output=True,text=True,timeout=10)
 check('Audio eligibility smoke test '+_result.stdout.strip(),_result.returncode==0)
except Exception as _e:check('Audio eligibility smoke test: '+str(_e),False)

# LESKOV 3.3 screen/renderer/media regressions.
check('No root CSS zoom that breaks fixed modals', 'html { zoom:' not in web)
check('WebView renderer death is handled', 'onRenderProcessGone(' in web and 'activity.recreate()' in web)
check('Native playback state reaches newly loaded PHP pages', 'publishNativeSessionToPage(it)' in web)
check('Media bridge supplies position and duration', 'positionMs:pos,durationMs:len' in web)
check('Media3 state is never displayed as a second native bottom bar', 'LeskovMiniPlayer(' not in main)
check('Media notification publishes real duration', 'METADATA_KEY_DURATION' in media)
check('Media playlist arrows depend on site capabilities', 'if (canNext) builder.addAction' in media)
check('Artwork bitmaps are downsampled for memory safety', 'inJustDecodeBounds = true' in media and '512' in media)
check('Adaptive launcher has white backing', '#FFFFFFFF' in (root/'app/src/main/res/drawable/ic_launcher_background.xml').read_text())
check('Guest authorization gate is compact', '.widthIn(max = 370.dp)' in auth)
check('Direct feed URL is never replaced with profile navigation', 'path = "feed.php"' in (root/'app/src/main/java/com/example/model/NavigationItem.kt').read_text())
check('Navigation awaits actual PHP load for currentUrl', 'currentUrl = targetUrl' not in (root/'app/src/main/java/com/example/viewmodel/LeskovViewModel.kt').read_text().split('fun onTabSelected(')[1].split('fun clearAppCache(')[0])

print(f'Static checks: {len(passed)} PASS, {len(errors)} FAIL')
for x in errors:print('FAIL:',x)
if errors:sys.exit(1)
