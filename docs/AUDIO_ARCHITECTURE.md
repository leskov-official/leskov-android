# Музыкальный движок LESKOV: текущее состояние и корректный путь

## Что выяснено по коду

Веб-сайт использует `scripts/player.js` с `window.__leskovPlayerManager`, активным плеером `active`, методом `pauseAll(except)` и HTMLMediaElement. В Android-проекте создан `LeskovMediaService` с уведомлением/блокировкой CPU, но он **не декодирует аудио и не владеет потоком**. Поток играет в Chromium WebView. Это принципиально: `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` и WakeLock сами по себе не делают плеер устойчивым к навигации и убийству процесса.

В предыдущем коде Android-bridge отправлял состояние paused для ещё не готового к декодированию трека. Также кнопки уведомления могли действовать на первый `audio`, а не на текущий плеер. Эти механизмы исправлены. Небезопасный `addJavascriptInterface` заменён origin-validated listener (WebView AndroidX).

## Чего ещё нет

**Нет автономного Media3 ExoPlayer/MediaSessionService, получающего корректный серверный URL медиа.** Без него полная загрузка другого PHP-документа уничтожает проигрывающийся DOM-элемент; гарантировать непрерывное звучание между вкладками пока нельзя.

## Архитектура для production

1. Согласовать с PHP-разработчиком легальный авторизованный endpoint выдачи потока/трека, права Premium, TTL ссылок, Content-Type, Range requests, защита от несанкционированного доступа. Не делать Premium-обход.
2. Сделать **Media3 `MediaSessionService`** единственным владельцем фактического аудиопотока (ExoPlayer), очереди, позиции, громкости и аудиофокуса.
3. Связать web UI и native player двусторонним **origin-scoped protocol**: source ID, track ID, play/pause/seek, metadata, queue, state, error. WebView больше не должен играть тот же трек параллельно.
4. Включить MediaStyle notification, lockscreen, Bluetooth/headset buttons, audio becoming noisy, корректные права foreground-service. При старте из фона соблюдать ограничения новых Android API.
5. Тесты: 1 минута воспроизведения, переходы по 6 вкладкам, экран выключен, foreground/background, входящий звонок, наушники, смена сети, отсутствие двойного аудио и Premium-подмена.

До этих интеграций **полноценная фоновая музыка не может считаться исправленной**. Этот документ служит техническим планом, а не утверждением о готовой функции.
