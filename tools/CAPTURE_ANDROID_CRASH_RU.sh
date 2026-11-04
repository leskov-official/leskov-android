#!/usr/bin/env bash
# Run inside Chromebook Linux (penguin). Never paste tokens or private logs publicly.
set -euo pipefail
if ! command -v adb >/dev/null; then
  echo 'Установи adb: sudo apt install adb'; exit 1
fi
echo 'Подключи Android USB, включи отладку по USB и разреши RSA-ключ на телефоне.'
adb devices
read -r -p 'Если устройство показано как device, нажми Enter: '
adb logcat -c
read -r -p 'Воспроизведи вылет LESKOV на телефоне, затем нажми Enter: '
OUT="${HOME}/LESKOV_CRASH_$(date +%Y%m%d_%H%M%S).txt"
{
  echo 'LESKOV crash buffer'
  adb logcat -d -b crash -v time
  echo
  echo 'LESKOV renderer/service diagnostic log'
  adb logcat -d -v time -s AndroidRuntime:E LeskovWebView:E LeskovNativeAudio:E LeskovMediaService:E ActivityManager:E LeskovNavigation:D | tail -n 250
} > "$OUT"
echo "Сохранено: $OUT"
echo 'Проверь файл на персональные данные перед передачей в чат.'
