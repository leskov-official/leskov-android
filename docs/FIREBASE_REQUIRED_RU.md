# Firebase LESKOV

Зарегистрирован проект `leskov-b3863`, Android package `com.aistudio.leskov.musix`.
Предоставленный владельцем `google-services.json` проверен и установлен локально
в `app/google-services.json`. Файл исключён из Git. В GitHub Actions используется
секрет `FIREBASE_ANDROID_CONFIG_BASE64`; статическая проверка сверяет проект и package.

Серверная интеграция добавлена и применена в `/var/www/lesv.online`.
Файлы, патч и оставшиеся действия: [backend/README_RU.md](../backend/README_RU.md).

До подключения серверной сервисной учётной записи и проверки на телефоне
сквозная доставка FCM не подтверждена. Обзор Firebase Console не был доступен
в авторизованном виде из среды агента; настройки API/IAM не проверены.
