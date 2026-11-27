#!/usr/bin/env python3
"""Static checks, not a replacement for Android Gradle or on-device tests."""
from pathlib import Path
import hashlib, re, struct, subprocess, sys, xml.etree.ElementTree as ET
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
  from firebase_config import valid_config
  check('Firebase project, matching package, app ID and key format', valid_config(firebase))
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
check('Media notification uses LESKOV monochrome icon', '.setSmallIcon(R.drawable.ic_notification_leskov_brand)' in media)
web=(root/'app/src/main/java/com/example/ui/components/LeskovWebView.kt').read_text()
check('Secure origin-limited WebMessageListener used', 'WebViewCompat.addWebMessageListener' in web)
check('JavascriptInterface not used for audio bridge', 'addJavascriptInterface(' not in web)
check('No invasive getUserMedia monkey-patch', 'origGetUserMedia' not in web)
bridge=(root/'app/src/main/assets/leskov-player-bridge.js').read_text()
check('Audio observer handles playing and error events', "'playing'" in bridge and "'error'" in bridge and 'addEventListener(name, onEvent' in bridge)
push=(root/'app/src/main/java/com/example/push/FcmTokenManager.kt').read_text()
check('No fabricated token upload endpoint', 'profile.php?ajax=register_fcm_token"' not in push)
check('No unverified HTTP client token uploads', 'httpClient.newCall(' not in push)
call=(root/'app/src/main/java/com/example/push/LeskovFirebaseMessagingService.kt').read_text()
check('Fake call answer button removed', 'addAction(' not in call)
dialog=(root/'app/src/main/java/com/example/ui/components/LeskovClearCacheDialog.kt').read_text()
check('Real site session logout route used', '__leskovAppShell.logout' in dialog and "action:'logout'" in (root/'app/src/main/assets/leskov-app-shell.js').read_text())
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
# Executable asset syntax and player behavior, rather than source-string checks alone.
for asset in (root/'app/src/main/assets').glob('*.js'):
 r=subprocess.run(['node','--check',str(asset)],capture_output=True,text=True,timeout=10)
 check(f'JS asset syntax {asset.name}',r.returncode==0)
r=subprocess.run(['node',str(root/'tools/test_player_bridge.js')],capture_output=True,text=True,timeout=10)
check('Premium player bridge regressions '+r.stdout.strip(),r.returncode==0)
r=subprocess.run(['node',str(root/'tools/test_app_shell.js')],capture_output=True,text=True,timeout=10)
check('App shell regressions '+r.stdout.strip(),r.returncode==0)
# The web engine remains the only music owner across navigation.
check('Calls excluded from music notifications', 'a.srcObject' in bridge and 'call[_-]?ringtone' in bridge)
check('Second playback service removed', 'LeskovNativeAudioService' not in manifest and not (root/'app/src/main/java/com/example/media/LeskovNativeAudioService.kt').exists())
check('No playback badge injected', "createElement('button')" not in bridge)
main=(root/'app/src/main/java/com/example/MainActivity.kt').read_text()
check('System status bar inset protects web content', '.windowInsetsPadding(WindowInsets.statusBars)' in main)
check('Bottom navigation responds to IME', 'keyboardVisible' in main and 'WindowInsets.ime' in main)
check('Guest gate and offline overlays animate', main.count('AnimatedVisibility(') >= 4)
check('Runtime web media grant checks current first-party page', 'isTrustedCurrentPage()' in main)
nav=(root/'app/src/main/java/com/example/ui/components/LeskovBottomBar.kt').read_text()
check('Compact six-tab navigation without scrolling', 'animateColorAsState(' in nav and 'horizontalScroll(' not in nav and '.weight(1f)' in nav)
auth=(root/'app/src/main/java/com/example/ui/components/LeskovAuthGate.kt').read_text()
check('Animated auth button feedback', 'collectIsPressedAsState' in auth and '.scale(loginScale)' in auth)
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
# LESKOV 3.3 screen/renderer/media regressions.
check('No root CSS zoom that breaks fixed modals', 'html { zoom:' not in web)
check('WebView renderer death is handled', 'onRenderProcessGone(' in web and 'activity.recreate()' in web)
check('Site soft navigation used for native tabs', '__leskovNavigateWithPlayer' in (root/'app/src/main/assets/leskov-app-shell.js').read_text())
check('Media bridge supplies position and duration', 'positionMs:' in bridge and 'durationMs:' in bridge)
check('No second native player or handoff', 'LeskovMiniPlayer(' not in main and 'AudioHandoff' not in main)
check('Media notification publishes real duration', 'METADATA_KEY_DURATION' in media)
check('Media playlist arrows depend on site capabilities', 'if (canNext) builder.addAction' in media)
check('Artwork bitmaps are downsampled for memory safety', 'inJustDecodeBounds = true' in media and '512' in media)
# The installer and launcher must resolve the original website image, not the old adaptive layers.
_android_ns = '{http://schemas.android.com/apk/res/android}'
_application = ET.fromstring(manifest).find('application')
_icon_ref = '@drawable/leskov_application_icon'
check('Application and round icon use identical original LESKOV bitmap',
      _application is not None and all(_application.get(_android_ns + name) == _icon_ref for name in ('icon', 'roundIcon')))
check('Launcher does not override original application icon', _application is not None and
      all(not node.get(_android_ns + 'icon') and not node.get(_android_ns + 'roundIcon')
          for tag in ('activity', 'activity-alias') for node in _application.findall(tag)))
_icon_files = list((root/'app/src/main/res').glob('*/leskov_application_icon.*'))
_icon = root/'app/src/main/res/drawable-nodpi/leskov_application_icon.png'
check('Original application icon has no density or adaptive override', _icon_files == [_icon])
_icon_bytes = _icon.read_bytes() if _icon.is_file() else b''
check('Application icon is original website PNG (SHA-256)',
      hashlib.sha256(_icon_bytes).hexdigest() == '0cf5febfc71737db8291b4ed4326ee8bfec44a68968ce54a6253d938a94b2b7f')
check('Original application icon has 512x512 RGBA pixels', len(_icon_bytes) >= 26 and
      _icon_bytes[:8] == b'\x89PNG\r\n\x1a\n' and _icon_bytes[12:16] == b'IHDR' and
      struct.unpack('>II', _icon_bytes[16:24]) == (512, 512) and _icon_bytes[24:26] == b'\x08\x06')
check('Guest authorization gate is compact', '.widthIn(max = 370.dp)' in auth)
check('Direct feed URL is never replaced with profile navigation', 'path = "feed.php"' in (root/'app/src/main/java/com/example/model/NavigationItem.kt').read_text())
check('Navigation awaits actual PHP load for currentUrl', 'currentUrl = targetUrl' not in (root/'app/src/main/java/com/example/viewmodel/LeskovViewModel.kt').read_text().split('fun onTabSelected(')[1].split('fun clearAppCache(')[0])

print(f'Static checks: {len(passed)} PASS, {len(errors)} FAIL')
for x in errors:print('FAIL:',x)
if errors:sys.exit(1)
