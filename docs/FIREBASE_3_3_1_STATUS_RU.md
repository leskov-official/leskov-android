# LESKOV 3.3.1 — проверка 4 ноября 2026

Android-код: c4a005d. GitHub Actions: https://github.com/leskov-official/leskov-android/actions/runs/41824617607

Подтверждено журналом CI: секрет FIREBASE_ANDROID_CONFIG_BASE64 присутствует,
конфигурация проекта leskov-b3863 с package com.aistudio.leskov.musix проходит
проверку; assembleDebug, testDebugUnitTest и lintDebug завершены успешно.
APK находится в артефакте LESKOV-Android-debug-APK указанной сборки.

На сервере PHP-проверки синтаксиса прошли; таблица mobile_push_devices создана
и доступна PHP-FPM. Публичный mobile_push.php без сессии возвращает 401 JSON.
Патч серверных файлов проверен через git apply --check на копиях исходных файлов.

Chromium/Playwright, viewport 412×915, тестовые ответы signaling без реальных
аккаунтов/звонков: feed.php, discover.php, search.php — показ окна и отправка
отклонения для звонка из messages; показ окна и закрытие после удалённой отмены
для звонка из profile. Ошибок JavaScript не обнаружено. Это не проверка
реального WebRTC между устройствами или доставки FCM.

Осталось: сервисная учётная запись Firebase отсутствует; авторизованного доступа
к Firebase Console нет. Состояние Cloud Messaging API v1 и IAM не проверено.
FCM-тест на телефоне (в фоне, на экране блокировки, после выхода) не выполнен.
Подробности подключения: backend/README_RU.md.
