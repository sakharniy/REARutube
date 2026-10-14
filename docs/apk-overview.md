# Разбор APK: Rutube для Android TV 31.18.0

## Файл

| Параметр | Значение |
|---|---|
| Источник | `https://rutube.ru/androidtv/latest/` → `promo.rutubelist.ru/apps/androidtv/RutubeTv.release.31.18.0.TV-general.411800001.apk` (ссылка со страницы rutube.ru/smarttv) |
| Размер, дата | 25 229 091 байт, 5 октября 2026 |
| SHA-256 | `f78aee534bd58329dae176adecf5222121a37f9918aa9c107036f47f8e955c84` |
| Подпись | APK Signature v2, `CN=Sergey Tikhonov, O=Rutube, L=Moscow, C=RU`, SHA-256 сертификата `e907019f373b17cb2efc3525d738d636a99347ee3e0bc683dfba466cad1841d9` |
| Пакет, версия | `ru.rutube.app`, `31.18.0.TV-general` (411800001) |
| Android | minSdk 24 (7.0), targetSdk 36 |
| Код | 3 DEX (~24 МБ), 17 810 классов → 24 348 файлов `.java` (jadx 1.5.6; 346 ошибок декомпиляции, для такого размера это норма) |

## Архитектура

- Kotlin и **Kotlin Multiplatform** (`ru.rutube.multiplatform.*`): общий код с мобильной версией.
- Сеть: OkHttp + **Retrofit**. Аннотации `retrofit2.http.*` обфусцированы в `jo.a…jo.y` по алфавиту исходных имён: `jo.f` = GET, `jo.o` = POST, `jo.s` = Path, `jo.t` = Query, `jo.y` = Url. В KMP-части (например, `play/options`) — Ktor.
- DI: Koin. Интерфейс: Fragments + Jetpack Compose, `androidx.tv`. Плеер: **AndroidX Media3 (ExoPlayer)** с плагинами `ru.rutube.player.plugin.*`.
- Интерфейс **серверный**: меню и экраны приходят из Pangolin (`/pangolin/api/tv/...`), клиент рисует вкладки и ряды карточек.
- Пакеты `ru.rutube.*` (~5000 классов) **не обфусцированы** и читаются по именам; обфусцированы только библиотеки.

## Карта модулей (главное)

| Пакет | Что внутри |
|---|---|
| `showcase` | модели экранов: feed, tabs, resources |
| `tv.feature.sidebar` | боковое меню ТВ (`navigation/Android_TV`) |
| `app.ui.fragment.TabContainerFragment` | соответствие пунктов меню лентам Pangolin |
| `player.playoptionsprovider` | запрос `play/options` с ТВ-параметрами |
| `player.plugin.*`, `player.tv` | плеер, end-screen, пропуск заставок |
| `adsdk.video`, `adsdk.video_common_vast`, `adsdk.banner` | рекламный SDK: VAST и баннеры |
| `rupassauth`, `common.auth`, `ru.gid.sdk` | вход: device flow, QR, GID |
| `multiplatform.core.networkclient` | сетевой клиент, хосты окружений prod/dev/qa/vip |
| `common.experiments` | A/B-эксперименты (`api/split?cid=`) |
| `common.debugpanel` | отладочная панель разработчиков |
| `multiplatform.shared.paidsubscriptions`, `payment`, `promocode` | платные подписки и оплата |

## Сторонние SDK и трекеры

- Реклама: собственный `ru.rutube.adsdk` + Яндекс AdFox (VAST); рекламные ID Google и Huawei.
- Аналитика: Yandex AppMetrica (~2200 классов), Firebase (Analytics, Crashlytics, Performance, Remote Config, Messaging), Sentry, Segment, Vigo (качество видео), Mediascope TNS (`tns-counter.ru`).
- Антифрод: `com.fraud.prevention.mobile_kit` + `libkfp-jni-lib.so`.
- Вход: GID SDK (`auth.gid.ru`, `libgid-cipher-lib.so`, капча).
- Аудиоводяной знак: `ru.rutube.player.audio_watermarker` + `libwatermarker.so` подмешивают метку в звук при воспроизведении (назначение по коду не установил).
- Разрешения: геолокация, микрофон (голосовой поиск), состояние телефона, аккаунты, рекламный ID, интеграция с PatchWall Xiaomi (`com.mitv.patchwall.permission.MANAGE_MEDIA`), каналы на главном экране Android TV (EPG).

## Сеть и сертификаты

- `network_security_config`: системные сертификаты + вшитый **Russian Trusted Root CA** (Минцифры, действует до 2032); незашифрованный HTTP разрешён только для `certs.yoomoney.ru`.
- Цепочки в проде на 13.10.2026:
  - `rutube.ru`, `bl.rutube.ru` — Let's Encrypt через ISRG Root X1 (Android 7.1.1 и новее);
  - `river-*.rutube.ru`, `static.rutube.ru` — TrustAsia с кросс-подписью Certum;
  - `*.rtbcdn.ru`, `pic.rutubelist.ru` — GlobalSign R6 с кросс-подписью R1.
- Российский корень пока ни одному из этих хостов не нужен, но приложение к нему готово. Своему клиенту стоит вшить его так же, на случай переезда.

## Буферизация видео

- Видео на диск не пишется: дискового кэша Media3 (`SimpleCache`) в коде нет.
- Буфер — свой класс `ai0.a` (в jadx `ai0/C0164a`), наследник `DefaultLoadControl`. Держит 15 с вперёд (min = max = 15 000 мс, приоритет времени над объёмом), просмотренное сразу выбрасывает.
- Рекламу грузит всегда, без лимита (`MediaPeriodId.isAd()` → сразу `true`). Для основного видео есть два переключателя: остановить загрузку совсем и не грузить на паузе. Кто их переключает, не разбирал.
- HLS нарезан кусками по 4 с (`#EXT-X-TARGETDURATION:4`, проверено на одном ролике 13.10.2026).
- REA Rutube держит 50 с вперёд (по умолчанию в Media3), а с 0.4 ещё 30 с назад, чтобы ← не скачивал всё заново. В прямых эфирах назад ничего не держит. Видео на диск тоже не пишет.

## Выводы для своего клиента

1. Всё для первой версии (меню, главная, разделы, эфиры, поиск, каналы, плеер) доступно анонимно — проверено.
2. Интерфейс «как у Rutube» достаётся почти даром: те же экраны Pangolin + универсальный рендер «вкладки → ряды → карточки».
3. Рекламу не нужно вырезать: не обрабатываем `advert` и `stat` и не ходим на рекламные хосты.
4. Плеер: Media3 + HLS из `video_balancer.m3u8` или `live_streams.hls`; `play/options` запрашиваем перед каждым запуском.
5. Не трогаем: контент с `drm_token`, Premier и платное, оплату.
6. Вход по коду (device flow) — отдельный этап, если понадобится раздел «Моё».
