# Rutube API: карта по APK для Android TV

Источник: официальный `RutubeTv 31.18.0` (подробности в [apk-overview.md](apk-overview.md)).
Всё ниже проверено вживую 2026-10-13 **без авторизации** скриптом [`scripts/probe_api.py`](../scripts/probe_api.py), кроме мест с пометкой «не проверял». Образцы ответов лежат локально в `re/samples/`. Полный список из 76 эндпоинтов Retrofit — в [endpoints.md](endpoints.md).

## Общие правила

- **Базы:** `https://rutube.ru/api/` — основной API; `https://rutube.ru/pangolin/api/tv/` — BFF «Pangolin»: меню и экраны для ТВ.
- **User-Agent обязателен.** Дефолтный UA curl получает `403 Forbidden` от WAF; браузерный или `okhttp/4.12.0` — `200`. Больше для анонимного чтения ничего не нужно.
- Заголовки самого приложения (для анонимного чтения не обязательны): `x-client-version: 31.18.0`, `x-device-Id`, `x-device-type`, `x-os-name`, `x-os-version`; после входа — `Authorization: Bearer <access_token>`.
- **Пагинация:** `results` + `has_next` + `next` (готовый URL следующей страницы); у поиска ещё `count` и `current_page`.
- **Ссылки на поток подписаны** (`sign`/`expire` у записей, `s`/`e` у эфиров) и протухают: `play/options` запрашиваем прямо перед воспроизведением.

## Хосты

| Хост | Что там | Клиенту |
|---|---|---|
| `rutube.ru/api/` | основной API | нужен |
| `rutube.ru/pangolin/api/tv/` | меню и экраны ТВ | нужен |
| `bl.rutube.ru` | HLS-манифесты: `route/<id>.m3u8`, `livestream/<id>/index.m3u8` | нужен |
| `river-*.rutube.ru`, `*.rtbcdn.ru` | сегменты видео | нужен (через плеер) |
| `pic.rutubelist.ru` | картинки и превью | нужен |
| `ac.rutube.ru` | рекламный SDK: `api/v1/banner_rules`, `api/v1/tlog`, события `api/v1/ev` | **не вызывать** |
| `a.rutube.ru` | собственный VAST-сервер Rutube | **не вызывать** |
| `adsdk.yandex.ru` (AdFox) | VAST-реклама Яндекса | **не вызывать** |
| `goya.rutube.ru` | события показов рекламы и баннеров | **не вызывать** |
| `log.rutube.ru`, `tns-counter.ru` и прочее из поля `stat` | логи и счётчики аудитории | не вызывать |
| `auth.gid.ru`, `multipass/api/...` | вход в аккаунт | позже |

## 1. Меню и экраны (интерфейс описывает сервер)

**Меню:** `GET pangolin/api/tv/navigation/Android_TV` (+ `x-client-version`) → `{result: [{slug, name, links: [{title, url}]}]}`. Две группы: боковое меню и «Моё».

| Пункт меню | Диплинк `rutube://rutube.ru/…` | Экран (`GET`) |
|---|---|---|
| Поиск | `search` | экран поиска, см. §3 |
| Главная | `main` | `pangolin/api/tv/feeds/tab_new_main/` |
| Категории | `catalog` | `pangolin/api/tv/feeds/smart_category/` |
| Кино и сериалы | `movies-serials/` | `pangolin/api/tv/feeds/movies-serials/` |
| Детям | `kids` | `pangolin/api/tv/feeds/kids/` |
| Спорт | `sport` | `pangolin/api/tv/feeds/sport/` |
| ТВ-каналы | `tv-channels` | `pangolin/api/tv/feeds/live_smarttv/` |
| Промо (сейчас «Верни 2006») | `feeds/<slug>/` | `pangolin/api/tv/feeds/<slug>/` |
| Premier (платное) | — | `pangolin/api/tv/feeds/premier/` |
| Моё: подписки, смотреть позже, плейлисты, история, понравилось | `my/…` | нужен вход |

Соответствие взято из `TabContainerFragment`; для «Главной» явной строки нет, но `tab_new_main` в ТВ-варианте Pangolin отвечает (`feeds/main/` — 404).

**Экран (feed):** `{id, slug, name, tabs: [{name, slug, resources: [{name, content_type, url}]}]}`.
Каждый `resource` — это ряд карточек, а его `url` — готовый запрос к API, например:
- `api/tags/video/<id>/?sort=tagged_d&limit=20` — подборка по тегу;
- `api/feeds/cardgroup/<id>?limit=20` — группа карточек (каналы, сериалы, фильмы);
- `api/v1/feeds/promogroup/<id>?limit=20` — промо-группа;
- `api/metainfo/tv/<id>/video/?limit=50` — выпуски шоу;
- `api/subscription/video/?authorized=true…` — «Новое из подписок» (нужен вход).

Главная ТВ на 13.10.2026: вкладки «Каталог», «Главная» (16 рядов), «ТВ онлайн», «Видеоигры», «Телешоу», «Спорт», «Трансляции».

**Карточка видео** (элемент `results`): `id` (32 hex), `title`, `thumbnail_url`, `duration`, `author{id, name, avatar_url}`, `is_livestream`, `is_on_air`, `is_paid`, `is_adult`, `pg_rating`, `publication_ts`, `hits`, `category`.

## 2. Видео

| Запрос | Что даёт |
|---|---|
| `GET api/video/<id>/` | полная карточка: описание, теги, `season`, `episode`, `tv_show_id`, `pepper` |
| `GET api/play/options/<id>/?<ТВ-параметры>` | поток, субтитры, аудиодорожки, реклама, счётчики |
| `GET api/video/recommendation/<id>/` | похожие (список из 20) |
| `GET api/v2/video/end-screen/<id>/?limit=0` | `next_video` и рекомендации после окончания; у серии `next_video` — следующая серия |
| `GET api/numerator/video/<id>/vote` | `{positive, negative}` |
| `GET pangolin/api/tv/video/<id>/annotation?featureUseCount=0&author_id=<канал>&client=tv` | кнопки поверх видео: заставка, титры, предупреждения |

**Отметки `annotation`** (в коде официального приложения — `@PangolinSource` в `skipintroandtitles`). Без `client=tv` сервер отвечает 400: «Допустимыми клиентами являются: wdp, web, tv, ios, android». Ответ — `activities[]`:
- `SKIP_INTRO` — кнопки «Смотреть заставку» (`HIDE`) и «Пропустить» (`SEEK_EXACTLY`, `target_position_ms`), видны с `show.start_position_ms` до `show.end_position_ms`;
- `SKIP_TITLES` — «Смотреть титры» (`HIDE`) и «Следующая серия» (`SWITCH_VIDEO`, `video_id`), у второй `progress: {AUTO_TRIGGER, duration_ms: 6000}` — сама переключает через 6 с;
- `DISCLAIMER` — тексты предупреждений по 3 с; приходят и у обычных видео.

Пример, «Бывшие», 1 сезон, 1 серия (13.10.2026): заставка 0:00–0:10, титры 50:35–51:14, дальше 2 серия.

**Платное по подписке.** У таких видео `is_paid: false`, но непустой `common_subscription_product_codes` (`RUTUBE_START`, `PREMIER_RUTUBE_START`, `vse-vklyucheno`). `play/options` отдаёт их без `video_balancer`, зато с `detail: {type: common_subscription, languages: [{title: "Смотрите по подписке RUTUBE", description: "Оформить можно на сайте"}]}`. Сериалы START на 13.10.2026: бесплатна только 1 серия, разметка заставки и титров есть именно у них.

**Следующая серия.** `end-screen.next_video` у сериалов обычно правильная (PREMIER, START, аккуратные каналы), но не всегда: у «Ведьмака» на одном канале после 1 серии предлагается 3-я, на другом — ролик про GTA. Клиент проверяет номер серии по названию и, если не сходится, ищет `«<сериал> <сезон> сезон <N+1> серия»` на том же канале.

**ТВ-параметры `play/options`** (из `ru.rutube.player.playoptionsprovider`):
`pver=v2&referer=https://tv-androidtv.rutube.ru&ver=31.18.0&vr_ver=2&av1=0|1&mq=…&ac_client=android_tv&ad_ver=v2&ac_request_id=<UUID>` плюс `Cookie`.
Последние три параметра рекламные. Без них (`?no_404=true`) поток выдаётся так же.

**Ответ `play/options`:**
- `video_balancer.m3u8` — HLS-мастер записи (H.264 + AAC, 144p–1080p, проверено ffprobe);
- `live_streams.hls[].url` — HLS эфира, `is_dvr` — можно ли перематывать;
- `captions`, `audiotracks` — субтитры и дорожки;
- `drm_token` — не `null` у защищённого контента: такие видео пропускаем;
- `advert` — реклама (§5), `stat` — счётчики (игнорируем).

## 3. Поиск

| Запрос | Что даёт |
|---|---|
| `GET api/search/combined/cards/list?query=` | общий поиск ТВ: каналы и видео карточками |
| `GET api/search/video/?query=` | только видео, с пагинацией |
| `GET api/search/autocomplete/video/?query=` | подсказки при вводе |
| `GET api/v2/search/history/` | история поиска (нужен вход) |

## 4. Каналы и плейлисты

| Запрос | Что даёт |
|---|---|
| `GET api/profile/user/<author_id>` | шапка канала: имя, описание, аватар, `video_count` |
| `GET api/video/person/<author_id>/?page=` | видео канала, есть `num_pages` |
| `GET api/v1/channels/<author_id>/pinned_playlists?limit=&limit_video=` | закреплённые плейлисты (`204`, если их нет) |
| `GET api/playlist/custom/<playlist_id>/` | плейлист |

## 5. Реклама: откуда берётся и как её не показывать

- Реклама **на стороне клиента** (CSAI): поток `video_balancer.m3u8` чистый, рекламных вставок в нём нет.
- В `play/options` → `advert.video.ad_breaks[]`: `name` (`preroll2`, `midroll8`, `postroll2`…), `one_time_starts`, `repeat{start, interval, count}`, `url_templates[]` по приоритету: 1) `adsdk.yandex.ru/v3/get_vast/...partner=adfox` (Яндекс AdFox), 2) `a.rutube.ru/api/v1/...`. Приложение подставляет `{{ifa}}`, `{{idfv}}`, `{{ru_cid}}` и проигрывает VAST своим `ru.rutube.adsdk`.
- `advert.stat` и корневой `stat` — пиксели: `goya.rutube.ru/v2/banner/...`, `ac.rutube.ru/api/v1/ev`, `tns-counter.ru`.
- Баннеры в интерфейсе (например, на экране поиска) — `ac.rutube.ru/api/v1/banner_rules` и `BannerApi`.
- **Итог для клиента:** игнорируем `advert` и `stat`, не ходим на `ac.`, `a.`, `goya.rutube.ru` и `adsdk.yandex.ru`. Вырезать ничего не нужно.

## 6. Что выяснилось при разработке клиента (0.2)

- **Ряды ТВ-экранов** в Pangolin имеют вид `pangolin/api/tv/resource/<лента>/<id>` и отвечают `{resourceId, results, has_next, next, page, per_page}`. Элементы бывают трёх форм:
  - карточка видео (как в поиске);
  - обёртка `{content_type: {model: "userchannel" | "tv"}, object: {id, name, picture, poster_url}}` — канал или шоу/фильм/сериал;
  - промо-плитка `{title, target: "/feeds/<slug>/", picture: ""}`.
- **Ряды каналов** (`custom_profile.userchannel`) через Pangolin отвечают 404, видео канала берём из `api/video/person/<object_id>/`.
- **Ряды только для аккаунта** («Новое из подписок») без входа отвечают 404, такие ряды скрываем.
- **Видео шоу и сериалов:** `api/metainfo/tv/<id>/video/?page=N`; профиль канала: `api/profile/user/<id>` → `{name, avatar_url, subscribers_count, video_count}`.
- **Картинки** на `pic.rtbcdn.ru` принимают `?size=s|m|l`: 193 / 480 / 640 px против 1920 px в оригинале (33 КБ против 329 КБ при `m`).
- **Иконки меню** приходят в PNG, но у промо-пунктов это SVG, а у пунктов «Моё» поле пустое.
- **Подсказки поиска:** `results: [{value, weight}]`. Экран окончания видео: `{next_video: <карточка>, recommendation: [{video}]}`.
- `av1=1` в `play/options` на проверенных роликах ничего не меняет: Rutube отдаёт H.264 до 1080p.

## 7. Вход в аккаунт (на будущее, не проверял)

OAuth 2.0 Device Flow (RFC 8628), интерфейс `il0.a`:
1. `POST multipass/api/v<N>/oauth2/device/code` → `device_code`, `user_code`, `verification_uri`, `interval`, `expires_in`;
2. ТВ показывает код или QR (`rupassauth.screen.qr.tv`), пользователь подтверждает на `rutube.ru/activate`;
3. `POST multipass/api/v<N>/oauth2/device/token` с `device_code` раз в `interval` секунд → `access_token`, `refresh_token`;
4. дальше `Authorization: Bearer …` для раздела «Моё».

Базовый хост `multipass`, тела запросов и `client_id` ещё не разбирал — это отдельный шаг, если понадобится вход.
