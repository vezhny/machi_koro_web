# Machi Koro Web

Веб-версия настольной игры Machi Koro (базовая игра). Сервер авторитарный: клиенты отправляют команды, сервер проверяет их по правилам, применяет и рассылает новое состояние всем участникам партии.

## Модули

| Модуль   | Назначение                                                      |
|----------|-----------------------------------------------------------------|
| `core`   | Правила игры, чистая Java без внешних зависимостей              |
| `server` | Spring Boot: REST-лобби и STOMP-канал игры                      |
| `web`    | Фронт (Vite + TypeScript), упаковывается в jar сервера          |
| `e2e`    | Сквозные тесты на Playwright, подключаются профилем `-Pe2e`      |

Документация: [docs/PROTOCOL.md](docs/PROTOCOL.md), [docs/OPEN_QUESTIONS.md](docs/OPEN_QUESTIONS.md).

## Требования

- JDK 21. Если JDK 21 не используется по умолчанию, укажите его через `JAVA_HOME`.
- Ставить Maven и Node.js не нужно: Maven скачивает Maven Wrapper, а Node подтягивает сборка модуля `web`.

## Сборка и тесты

```bash
./mvnw verify          # сборка и все тесты, кроме e2e
./mvnw verify -Pe2e    # то же самое плюс сквозные тесты
```

### Сквозные тесты (`-Pe2e`)

Модуль `e2e` запускает собранный jar сервера на свободном порту с профилем `test-dice` и играет через Chromium (Playwright for Java) за двух игроков в изолированных контекстах браузера. Chromium скачивается при первом запуске в кеш Playwright (`%LOCALAPPDATA%\ms-playwright` или `~/.cache/ms-playwright`).

- Запускать из корня репозитория: e2e использует `server/target/server-*.jar` из той же сборки.
- `-De2e.headed=true` открывает видимый браузер.
- Лог сервера пишется в `e2e/target/e2e-server.log`.

Профиль `test-dice` нужен только для тестов. Он добавляет эндпоинт `POST /api/test/dice` с телом `{"values":[3,4]}`: переданные значения выпадут в следующих бросках. Без этого профиля эндпоинта нет.

На Windows используйте `mvnw.cmd` вместо `./mvnw`.

## Запуск

```bash
./mvnw package -DskipTests
java -jar server/target/server-0.1.0-SNAPSHOT.jar
```

Откройте http://localhost:8080, создайте партию и отправьте ссылку (или 6-значный код) остальным игрокам. Играть можно и с двух вкладок одного браузера: каждая вкладка помнит своё место.

Сервер слушает порт 8080: фронт на `/`, REST на `/api/games`, WebSocket (STOMP) на `/ws`, проверка состояния на `/actuator/health`. Протокол описан в [docs/PROTOCOL.md](docs/PROTOCOL.md).

### Разработка фронта

Модуль `web` собирается из Maven: `frontend-maven-plugin` скачивает Node в `web/node`, затем выполняет `npm install` и `npm run build`. Для чистой установки (в CI) добавьте `-Dnpm.install=ci`. Локально это может не сработать, пока IDE держит открытым `node_modules/.../tsc.exe`. Чтобы пересобрать только бэкенд без фронта, добавьте `-Dskip.npm -Dskip.installnodenpm`.

Для живой перезагрузки запустите сервер, как описано выше, и Vite dev server. Vite проксирует `/api` и `/ws` на `:8080`:

```bash
cd web
npm install
npm run dev
```

Затем откройте http://localhost:5173.

### Docker

```bash
docker compose up --build
```

Образ собирается в два этапа. Сначала `./mvnw package` в `eclipse-temurin:21-jdk` (тесты на этом этапе не запускаются, ими занимается CI), затем jar копируется в `eclipse-temurin:21-jre` и запускается от непривилегированного пользователя. Приложение доступно на http://localhost:8080. Параметры JVM задаются переменной `JAVA_OPTS`.

### CI

[GitHub Actions](.github/workflows/ci.yml): на каждый push выполняется `./mvnw verify`, на каждый pull request — `./mvnw verify -Pe2e`. При падении отчёты тестов и лог сервера e2e сохраняются как артефакты.

### Windows: «Unable to establish loopback connection»

На некоторых машинах с Windows защитное ПО запрещает AF_UNIX-сокеты в `%TEMP%`, и Java не может открыть NIO-селектор. Из-за этого не стартуют Tomcat и HTTP-клиенты. Тесты Maven уже обходят это сами. Для ручного запуска укажите другой каталог:

```bash
java -Djdk.net.unixdomain.tmpdir=server/target -jar server/target/server-0.1.0-SNAPSHOT.jar
```

В IntelliJ IDEA добавьте то же свойство в VM options конфигурации запуска.
