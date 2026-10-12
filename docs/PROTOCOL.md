# Протокол клиент ↔ сервер

Сервер авторитарный: клиент только отправляет команды, а сервер проверяет их по правилам, применяет и рассылает новое состояние всем участникам партии.

- REST (`/api/...`): лобби и восстановление состояния.
- STOMP over WebSocket (`/ws`): команды хода и рассылка состояния.
- Все тела запросов и ответов в JSON. Значения перечислений передаются строками в UPPER_SNAKE_CASE: `"WHEAT_FIELD"`, `"TRAIN_STATION"`, `"BUILD"`.

## Авторизация

При создании партии и при входе в неё игрок получает `playerToken` (UUID). Токен показывается один раз, его нужно сохранить на клиенте (например, в `localStorage`).

| Где | Как передаётся |
|---|---|
| REST | заголовок `X-Player-Token: <token>` (нужен только для `start`) |
| STOMP | заголовок `X-Player-Token: <token>` во фрейме `CONNECT` |

Токен привязан к одному игроку в одной партии. После `CONNECT` сервер знает игрока, поэтому `playerId` в командах не передаётся.

---

## REST

### Ошибки

Любая ошибка возвращается так:

```json
{ "code": "GAME_FULL", "message": "Game KC7VVR is full" }
```

| HTTP | `code` | Когда |
|---|---|---|
| 400 | `BAD_REQUEST` | Пустое или слишком длинное имя (> 24 символов), некорректный JSON |
| 401 | `INVALID_TOKEN` | Нет заголовка `X-Player-Token` или токен неизвестен |
| 403 | `NOT_HOST` | Партию запускает не создатель |
| 403 | `WRONG_GAME` | Токен от другой партии |
| 404 | `GAME_NOT_FOUND` | Нет партии с таким id |
| 409 | `GAME_FULL` | Уже 4 игрока |
| 409 | `GAME_ALREADY_STARTED` | Вход или повторный старт после начала |
| 409 | `NOT_ENOUGH_PLAYERS` | Старт с одним игроком |

### `POST /api/games` — создать партию

Создатель сразу занимает первое место и становится хостом.

Запрос:
```json
{ "name": "Alice" }
```
Ответ `201 Created`:
```json
{
  "gameId": "KC7VVR",
  "playerId": "4f6c1d0e-7a35-4a4e-9a51-0b6a1f6f8e21",
  "playerToken": "b9c1e1a2-3d4f-4c8b-8e0a-5d2f1c7b9a10"
}
```
`gameId` состоит из 6 символов `A–Z` и `2–9` без похожих символов (`O/0`, `I/1`), чтобы код удобно было продиктовать.

### `POST /api/games/{gameId}/join` — войти в партию

Можно только до старта, максимум 4 игрока.

Запрос и ответ — как у `POST /api/games`, ответ `200 OK`. Всем подписанным на `/topic/games/{gameId}/lobby` уходит обновлённый `LobbyView`.

### `POST /api/games/{gameId}/start` — начать партию

Заголовок `X-Player-Token` хоста обязателен, нужно минимум 2 игрока. Ответ `200 OK` — `GameDetails` (см. ниже) со статусом `IN_PROGRESS` и начальным состоянием. Одновременно сервер рассылает:
- `LobbyView` со статусом `IN_PROGRESS` в `/topic/games/{gameId}/lobby`;
- `GameUpdate` с версией 0 и пустым списком событий в `/topic/games/{gameId}`.

Порядок хода совпадает с порядком входа: хост ходит первым.

### `GET /api/games/{gameId}` — состояние партии

Используется для переподключения. Токен не нужен, токены в ответе не возвращаются.

```json
{
  "gameId": "KC7VVR",
  "status": "IN_PROGRESS",
  "hostPlayerId": "4f6c…",
  "players": [
    { "id": "4f6c…", "name": "Alice" },
    { "id": "91aa…", "name": "Bob" }
  ],
  "state": { "…": "GameView, см. ниже; null пока status = LOBBY" }
}
```

`status`: `LOBBY` → `IN_PROGRESS` → `FINISHED`.

### `GET /api/games` — открытые лобби

Только партии со статусом `LOBBY`.

```json
[
  { "gameId": "KC7VVR", "hostName": "Alice", "playerCount": 1, "maxPlayers": 4 }
]
```

---

## STOMP

| | |
|---|---|
| Endpoint | `ws://<host>/ws` (чистый WebSocket, без SockJS) |
| Префикс команд | `/app` |
| Брокер | встроенный, `/topic` и `/queue` |

### Подключение

```
CONNECT
accept-version:1.2
X-Player-Token:b9c1e1a2-3d4f-4c8b-8e0a-5d2f1c7b9a10
```

С неверным токеном или без него сервер отвечает фреймом `ERROR` и закрывает соединение.

### Подписки

Разрешены только адреса своей партии:

| Адрес | Что приходит |
|---|---|
| `/topic/games/{gameId}` | `GameUpdate` после каждой принятой команды и при старте |
| `/topic/games/{gameId}/lobby` | `LobbyView` при входе игрока и при старте |
| `/user/queue/errors` | `ErrorMessage` на свои отклонённые команды |

Подписка на чужую партию отклоняется фреймом `ERROR`.

### Команды

Отправляются в `/app/games/{gameId}/commands`. В каждой команде есть `type` и `expectedVersion`: версия состояния, которое видел клиент. Если она не совпадает с текущей, команда отклоняется с кодом `STALE_VERSION`.

| `type` | Фаза | Поля | Описание |
|---|---|---|---|
| `RollDice` | `ROLL` | `diceCount`: 1 или 2 | Бросок. 2 кубика только с Train Station |
| `Reroll` | `REROLL_DECISION` | `diceCount`: 1 или 2 | Radio Tower: перебросить, можно сменить число кубиков |
| `KeepRoll` | `REROLL_DECISION` | — | Radio Tower: оставить результат |
| `ChooseTarget` | `AWAITING_TARGET` | `targetPlayerId`, `give`, `take` | Цель для TV Station или Business Center |
| `Build` | `BUILD` | `establishment` **или** `landmark` | Купить одну карту. **Завершает ход** |
| `Pass` | `BUILD` | — | Ничего не строить, завершить ход |

Примеры:

```json
{ "type": "RollDice", "expectedVersion": 0, "diceCount": 1 }
```
```json
{ "type": "Reroll", "expectedVersion": 1, "diceCount": 2 }
```
```json
{ "type": "KeepRoll", "expectedVersion": 1 }
```
```json
{ "type": "ChooseTarget", "expectedVersion": 2, "targetPlayerId": "91aa…" }
```
TV Station: забрать до 5 монет у выбранного соперника.
```json
{ "type": "ChooseTarget", "expectedVersion": 3, "targetPlayerId": "91aa…", "give": "WHEAT_FIELD", "take": "MINE" }
```
Business Center: отдать своё `give` и забрать `take` у соперника, фиолетовые карты не обмениваются.
```json
{ "type": "ChooseTarget", "expectedVersion": 3, "targetPlayerId": null }
```
Business Center: пропустить обмен.
```json
{ "type": "Build", "expectedVersion": 4, "establishment": "RANCH" }
```
```json
{ "type": "Build", "expectedVersion": 4, "landmark": "TRAIN_STATION" }
```
```json
{ "type": "Pass", "expectedVersion": 4 }
```

### `GameUpdate`: рассылка состояния

Приходит в `/topic/games/{gameId}` всем участникам после каждой принятой команды:

```json
{
  "state": {
    "gameId": "KC7VVR",
    "version": 1,
    "phase": "BUILD",
    "activePlayerId": "4f6c…",
    "players": [
      {
        "id": "4f6c…",
        "name": "Alice",
        "coins": 4,
        "establishments": { "WHEAT_FIELD": 1, "BAKERY": 1 },
        "landmarks": []
      },
      {
        "id": "91aa…",
        "name": "Bob",
        "coins": 4,
        "establishments": { "WHEAT_FIELD": 1, "BAKERY": 1 },
        "landmarks": []
      }
    ],
    "supply": { "WHEAT_FIELD": 6, "RANCH": 6, "BAKERY": 6, "…": 6, "STADIUM": 4 },
    "turn": { "dice": [1], "rerollUsed": false, "pendingTargets": [] },
    "winnerId": null
  },
  "events": [
    { "type": "DiceRolled", "playerId": "4f6c…", "dice": [1], "reroll": false },
    { "type": "IncomeReceived", "playerId": "4f6c…", "card": "WHEAT_FIELD", "amount": 1 },
    { "type": "IncomeReceived", "playerId": "91aa…", "card": "WHEAT_FIELD", "amount": 1 }
  ]
}
```

Поля `state` (`GameView`):

| Поле | Описание |
|---|---|
| `version` | Растёт на 1 с каждой принятой командой. Её нужно отправлять как `expectedVersion` |
| `phase` | `ROLL`, `REROLL_DECISION`, `AWAITING_TARGET`, `BUILD`, `GAME_OVER` |
| `activePlayerId` | Чей ход |
| `players` | В порядке хода. В `establishments` только карты с количеством > 0 |
| `supply` | Остаток в банке. Распроданной карты в объекте нет |
| `turn.dice` | Последний бросок, пустой до броска |
| `turn.pendingTargets` | Карты, ждущие `ChooseTarget`; текущая — первая |
| `winnerId` | `null`, пока `phase` не `GAME_OVER` |

### События

Приходят в порядке, в котором происходили. Нужны для анимаций и лога; источник истины — `state`.

| `type` | Поля | Когда |
|---|---|---|
| `DiceRolled` | `playerId`, `dice`, `reroll` | Бросок или переброс |
| `IncomeReceived` | `playerId`, `card`, `amount` | Доход из банка (синие, зелёные) |
| `CoinsTransferred` | `fromPlayerId`, `toPlayerId`, `card`, `amount` | Красные карты, Stadium, TV Station |
| `TargetRequired` | `playerId`, `card` | Нужно выбрать цель для `card` |
| `EstablishmentsSwapped` | `playerId`, `targetPlayerId`, `given`, `taken` | Business Center |
| `Built` | `playerId`, `target` | Покупка. `target` = `{"kind":"ESTABLISHMENT","establishment":"RANCH"}` или `{"kind":"LANDMARK","landmark":"RADIO_TOWER"}` |
| `TurnPassed` | `fromPlayerId`, `toPlayerId` | Ход перешёл |
| `ExtraTurnGranted` | `playerId` | Дубль с Amusement Park |
| `GameWon` | `playerId` | Построена 4-я достопримечательность |

События с нулевой суммой не создаются: например, Cafe не срабатывает, если у активного игрока 0 монет.

### `LobbyView`

```json
{
  "gameId": "KC7VVR",
  "status": "LOBBY",
  "hostPlayerId": "4f6c…",
  "players": [ { "id": "4f6c…", "name": "Alice" }, { "id": "91aa…", "name": "Bob" } ]
}
```

### Ошибки команд

Приходят **только отправителю** в `/user/queue/errors`. Состояние партии при этом не меняется, и рассылки нет.

```json
{ "code": "NOT_YOUR_TURN", "message": "It is 4f6c…'s turn" }
```

| `code` | Причина |
|---|---|
| `GAME_NOT_STARTED` | Партия ещё в лобби |
| `WRONG_GAME` | Команда в чужую партию |
| `BAD_REQUEST` | Некорректный JSON, неизвестный `type`, у `Build` нет цели или указаны обе |
| `GAME_OVER` | Партия окончена |
| `UNKNOWN_PLAYER` | Игрока нет в партии |
| `NOT_YOUR_TURN` | Сейчас ход другого игрока |
| `STALE_VERSION` | `expectedVersion` ≠ текущей версии |
| `WRONG_PHASE` | Команда не подходит к фазе (в том числе вторая покупка за ход) |
| `INVALID_DICE_COUNT` | `diceCount` не 1 и не 2 |
| `TRAIN_STATION_REQUIRED` | 2 кубика без Train Station |
| `INVALID_TARGET` | Цель не указана, неизвестна или это сам игрок |
| `INVALID_SWAP` | Для обмена указана карта, которой нет, или фиолетовая карта |
| `NOT_ENOUGH_COINS` | Не хватает монет |
| `SUPPLY_EMPTY` | Карта распродана |
| `PURPLE_ALREADY_OWNED` | Вторая одинаковая фиолетовая карта |
| `LANDMARK_ALREADY_BUILT` | Достопримечательность уже построена |

---

## Типичный сценарий клиента

1. `POST /api/games` или `POST /api/games/{id}/join`, затем сохранить `gameId`, `playerId` и `playerToken`.
2. Подключиться к `/ws` с `X-Player-Token`, подписаться на `/topic/games/{id}`, `/topic/games/{id}/lobby` и `/user/queue/errors`.
3. Хост вызывает `POST /api/games/{id}/start`, и все получают `GameUpdate` с версией 0.
4. Активный игрок отправляет команды с `expectedVersion = state.version`, и все получают новый `GameUpdate`.
5. При перезагрузке страницы: `GET /api/games/{id}`, затем повторное подключение и подписки (п. 2).
