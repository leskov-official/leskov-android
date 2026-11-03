# LESKOV — получение APK через GitHub Actions

Это **исходники Android**, а не APK. Сборка не проверена до первого реального запуска CI.

## Первый запуск

1. Создай **приватный** репозиторий `leskov-android` на GitHub: https://github.com/new. Не добавляй README, license и .gitignore, они уже в проекте.
2. Распакуй архив LESKOV_GITHUB_READY.zip. В репозиторий нужно загрузить **содержимое** папки проекта из архива на верхний уровень. Файлы `settings.gradle.kts`, `app/`, `gradle/` и `.github/workflows/android.yml` должны быть в корне репозитория (без папки `LESKOV_GITHUB_READY/` вокруг них).
3. Если загружаешь через GitHub Web: **Add file → Upload files**. Перетащи содержимое распакованной папки, проверь сохранение вложенных каталогов и особенно `.github/workflows/android.yml`, затем **Commit changes**.
4. Открой **Actions → LESKOV Android — build, test and APK**. При push в `main` workflow запускается автоматически; если нет — открой его и нажми **Run workflow**.
5. При успешной сборке открой run → **Artifacts → LESKOV-Android-debug-APK**; в скачанном архиве будет `app-debug.apk`. Распакуй его, перенеси на Android и установи для теста.
6. Если сборка красная, открой упавший шаг **Build Android debug APK**, скопируй лог и дай ChatGPT либо предоставь GitHub-приложению доступ к созданному репозиторию, чтобы ChatGPT мог прочитать ошибки.

## Firebase

Для обычной debug-сборки `google-services.json` **не обязателен**. В таком случае Firebase Cloud Messaging не будет работать.
Для проверки push сначала настрой собственный Firebase-проект с Android package **`com.aistudio.leskov.musix`**, затем сохрани реальную конфигурацию в GitHub Actions secret `FIREBASE_ANDROID_CONFIG_BASE64`. Не загружай приватные серверные ключи в репозиторий и не подставляй фиктивные Firebase значения.

## Безопасность

Не делай репозиторий публичным, не публикуй PHP-бэкенд и секреты. Этот проект нуждается в тестировании музыки, уведомлений и звонков на реальных устройствах. Успешная сборка APK не гарантирует готовность к Play Store.

## Примечание о совместимости

Версия androidx.core-ktx снижена до 1.17.0: 1.18.0 требует compileSdk 36.1, а этот проект использует compileSdk 36. Это предотвращает проверенную несовместимость версий на этапе Android build.
