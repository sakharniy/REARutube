# REA Rutube

Свой клиент RUTUBE для Android TV без рекламы. Сделан через reverse engineering официального приложения: разобрал APK, выяснил, как оно общается с сервером, и написал клиент заново на Kotlin.

> Неофициальный проект, к RUTUBE отношения не имеет. Сделан для себя и для обучения.
> Рекламу клиент не вырезает, а просто не запрашивает. Платное и защищённое (подписки, DRM) он не воспроизводит.
> Декомпилированный код официального приложения здесь не публикуется — только выводы из него.

[English below](#english)

## Что умеет

- **Как у RUTUBE:** боковое меню, главная и разделы приходят с сервера, есть прямые эфиры, каналы и поиск с экранной клавиатурой.
- **Плеер:**
  - качество «Авто» или вручную, скорость;
  - перемотка по 10 с, при удержании ускоряется;
  - продолжение с того же места;
  - кнопка канала;
  - «Следующая серия», в том числе у сериалов, которые залили обычные каналы;
  - «Пропустить заставку», если RUTUBE её разметил.
- **Без аккаунта:** подписки, «Смотреть позже» и история хранятся на самом телевизоре.
- **Диагностика:** декодеры, сеть, последние воспроизведения.

Проверялось на Xiaomi ES Pro 55 (Android 9, без сервисов Google). Нужен Android 7 или новее.

## Установка

1. Скачай APK из [Releases](../../releases/latest).
2. Скопируй его на флешку, вставь флешку в телевизор и открой APK файловым менеджером.
3. Если установка заблокирована, разреши установку из неизвестных источников. На Xiaomi это Settings → Account & Security.

Новые версии из Releases ставятся поверх старых, без удаления. На Xiaomi с китайской прошивкой удалять приложение лучше через «ТВ-менеджер»: стандартные настройки Android там вылетают.

## Управление с пульта

| Кнопка | Что делает |
|---|---|
| ← из контента | открывает боковое меню |
| OK | открыть; в плеере — показать панель управления |
| долгое OK на видео | добавить в «Смотреть позже» или убрать оттуда; в «Истории» — убрать из истории |
| долгое OK на канале | подписаться или отписаться |
| ←/→ в плеере | перемотка на 10 с; если держать — всё быстрее (30 с, 1 мин, 2 мин) |
| шестерёнка или ☰ в плеере | качество и скорость |
| кнопки внизу плеера (↓↓ из панели) | канал — его страница, «Назад» вернёт к видео; «Следующая серия» — у сериалов, серия N+1 того же канала |
| «Пропустить заставку», «Следующая серия» поверх видео | у сериалов, где RUTUBE разметил заставку и титры; «Назад» их прячет |
| Назад | на шаг назад; на главной дважды — выход |

## Как это сделано

Результаты разбора лежат в `docs/`:

- [разбор APK](docs/apk-overview.md) — архитектура, SDK и трекеры, сеть, буферизация видео;
- [карта API](docs/api.md) — меню, ленты, плеер, поиск, отметки заставки и титров, платный контент;
- [все эндпоинты](docs/endpoints.md) — список запросов официального приложения.

Повторить разбор (нужны [jadx](https://github.com/skylot/jadx) и [apktool](https://apktool.org/); официальный APK скачивается с сайта RUTUBE):

```bash
curl -L -A "Mozilla/5.0" https://rutube.ru/androidtv/latest/ -o apk/RutubeTv.apk
java -jar tools/apktool/apktool.jar d -f -s -o re/apktool apk/RutubeTv.apk
java -Xmx8g -cp tools/jadx/lib/jadx-1.5.6-all.jar jadx.cli.JadxCLI -d re/jadx -j 4 \
  --show-bad-code --deobf --use-source-name-as-class-name-alias if-better apk/RutubeTv.apk
python scripts/list_endpoints.py re/jadx/sources > re/analysis/endpoints.md
python scripts/probe_api.py re/samples
```

Папки `apk/`, `re/` и `tools/` в репозиторий не входят.

## Сборка

Нужны JDK 17 и Android SDK 36. Клиент написан на Kotlin, Compose for TV, Media3, OkHttp и Coil.

```bash
cd client
./gradlew :app:testReleaseUnitTest :app:assembleRelease
# → client/app/build/outputs/apk/release/app-release.apk
```

Без `client/keystore.properties` релиз подписывается отладочным ключом. Такую сборку нельзя поставить поверх версии из Releases: сначала удали старую. Чтобы подписывать своим ключом, положи рядом с `client/build.gradle.kts` файл `keystore.properties`:

```properties
storeFile=keystore/my.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Библиотеки подобраны под AGP 8.13. Более новые Compose 1.12, core 1.19 и OkHttp 5.5 требуют AGP 9.1 и compileSdk 37.

| Папка | Что там |
|---|---|
| `client/` | исходники клиента и тесты |
| `docs/` | результаты разбора |
| `scripts/` | скрипты анализа APK, проверки API и замера плавности |

---

## English

**REA Rutube** is an unofficial, ad-free RUTUBE client for Android TV. It was built by reverse engineering the official app: the APK was decompiled to learn how the app talks to the server, and the client was written from scratch in Kotlin.

> Not affiliated with RUTUBE. Made for personal use and learning.
> The client does not strip ads, it simply never requests them. It does not play paid or protected content (subscriptions, DRM).
> No decompiled code of the official app is published here, only the findings.

**Features**

- **Like RUTUBE:** the server-driven menu, home and sections, live streams, channels, and search with an on-screen keyboard.
- **Player:**
  - quality (auto or manual) and speed;
  - 10 s seeking that speeds up while the key is held;
  - resume from where you stopped;
  - a channel button;
  - "next episode", including series uploaded by ordinary channels;
  - "skip intro" where RUTUBE has marked it.
- **No account needed:** subscriptions, watch later and history are kept on the TV.

Tested on a Xiaomi ES Pro 55 (Android 9, no Google services). Requires Android 7 or newer.

**Install:** download the APK from [Releases](../../releases/latest), copy it to a USB stick and open it on the TV. Allow installs from unknown sources if asked. New releases install over old ones.

**Findings:** [APK overview](docs/apk-overview.md), [API map](docs/api.md), [all endpoints](docs/endpoints.md) (in Russian).

**Build:** you need JDK 17 and Android SDK 36. Run `cd client && ./gradlew :app:testReleaseUnitTest :app:assembleRelease`. Without `client/keystore.properties` the release build is signed with the debug key, so it cannot be installed over a release from this page.
