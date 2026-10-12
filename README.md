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

На Windows используйте `mvnw.cmd` вместо `./mvnw`.

## Запуск

```bash
./mvnw package -DskipTests
java -jar server/target/server-0.1.0-SNAPSHOT.jar
```

Откройте http://localhost:8080, создайте партию и отправьте ссылку (или 6-значный код) остальным игрокам. Играть можно и с двух вкладок одного браузера: каждая вкладка помнит своё место.

Сервер слушает порт 8080: фронт на `/`, REST на `/api/games`, WebSocket (STOMP) на `/ws`, проверка состояния на `/actuator/health`. Протокол описан в [docs/PROTOCOL.md](docs/PROTOCOL.md).

### Разработка фронта

Модуль `web` собирается из Maven: `frontend-maven-plugin` скачивает Node в `web/node`, затем выполняет `npm ci` и `npm run build`. Чтобы пересобрать только бэкенд без фронта, добавьте `-Dskip.npm -Dskip.installnodenpm`.

Для живой перезагрузки запустите сервер, как описано выше, и Vite dev server. Vite проксирует `/api` и `/ws` на `:8080`:

```bash
cd web
npm install
npm run dev
```

Затем откройте http://localhost:5173.

### Windows: «Unable to establish loopback connection»

На некоторых машинах с Windows защитное ПО запрещает AF_UNIX-сокеты в `%TEMP%`, и Java не может открыть NIO-селектор. Из-за этого не стартуют Tomcat и HTTP-клиенты. Тесты Maven уже обходят это сами. Для ручного запуска укажите другой каталог:

```bash
java -Djdk.net.unixdomain.tmpdir=server/target -jar server/target/server-0.1.0-SNAPSHOT.jar
```

В IntelliJ IDEA добавьте то же свойство в VM options конфигурации запуска.
