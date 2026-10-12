import {
  COLOR_NAMES,
  ESTABLISHMENT_ORDER,
  ESTABLISHMENTS,
  ICONS,
  LANDMARK_ORDER,
  LANDMARKS,
  isPurple,
  numbersLabel,
} from './cards';
import type { Establishment, GameView, LobbySummary, PlayerView } from './protocol';
import type { GameScreen, HomeScreen, PickSeatScreen } from './state';
import { PHASE_TEXT, coins, dieFace, escapeHtml as e } from './text';

// HTML templates. Every user-provided string (player names) goes through escapeHtml.
// Buttons carry data-action (+ data-* arguments) and are handled by delegation in main.ts.

const MAX_PLAYERS = 4;

// ---------- Home ----------

export function homeView(screen: HomeScreen, name: string): string {
  return `
  <main class="page narrow">
    <h1 class="logo">🎲 Machi Koro</h1>
    <section class="panel">
      <label class="field">
        <span>Ваше имя</span>
        <input id="name" maxlength="24" value="${e(name)}" autocomplete="nickname" placeholder="Например, Алиса">
      </label>
      <button class="primary wide" data-action="create">Создать партию</button>
      <form class="row" data-form="join-code">
        <input name="code" maxlength="6" placeholder="Код партии" autocapitalize="characters" spellcheck="false" required>
        <button type="submit">Войти по коду</button>
      </form>
      ${screen.error ? `<p class="error">${e(screen.error)}</p>` : ''}
    </section>
    <section class="panel">
      <div class="row between">
        <h2>Открытые партии</h2>
        <button class="ghost" data-action="refresh-lobbies">Обновить</button>
      </div>
      <div id="lobby-list">${lobbyListView(screen.lobbies)}</div>
    </section>
  </main>`;
}

export function lobbyListView(lobbies: LobbySummary[]): string {
  if (lobbies.length === 0) {
    return '<p class="muted">Пока никто не ждёт игроков — создайте свою партию.</p>';
  }
  return `<ul class="lobbies">${lobbies
    .map(
      (lobby) => `
      <li>
        <code>${e(lobby.gameId)}</code>
        <span class="grow">${e(lobby.hostName)}</span>
        <span class="muted">${lobby.playerCount}/${lobby.maxPlayers}</span>
        <button data-action="join-lobby" data-game="${e(lobby.gameId)}"
          ${lobby.playerCount >= lobby.maxPlayers ? 'disabled' : ''}>Войти</button>
      </li>`,
    )
    .join('')}</ul>`;
}

// ---------- Seat choice ----------

export function pickSeatView(screen: PickSeatScreen, name: string): string {
  const { details, seats } = screen;
  const canJoin = details.status === 'LOBBY' && details.players.length < MAX_PLAYERS;
  return `
  <main class="page narrow">
    <h1>Партия <code>${e(details.gameId)}</code></h1>
    ${
      seats.length > 0
        ? `<section class="panel">
            <h2>Продолжить как</h2>
            <div class="row wrap">${seats
              .map((s) => `<button class="primary" data-action="pick-seat" data-player="${e(s.playerId)}">${e(s.name)}</button>`)
              .join('')}</div>
          </section>`
        : ''
    }
    <section class="panel">
      ${
        canJoin
          ? `<h2>Войти новым игроком</h2>
             <p class="muted">Уже в партии: ${details.players.map((p) => e(p.name)).join(', ')}</p>
             <label class="field"><span>Ваше имя</span>
               <input id="name" maxlength="24" value="${e(name)}" autocomplete="nickname"></label>
             <button class="primary wide" data-action="join-new">Войти</button>`
          : `<p>${details.status === 'LOBBY' ? 'В партии нет свободных мест.' : 'Партия уже идёт, новые игроки войти не могут.'}</p>`
      }
    </section>
    <button class="ghost" data-action="home">← На главную</button>
  </main>`;
}

// ---------- Waiting room ----------

export function waitingRoomView(screen: GameScreen): string {
  const details = screen.details!;
  const isHost = details.hostPlayerId === screen.seat.playerId;
  const enough = details.players.length >= 2;
  return `
  <main class="page narrow">
    ${topBar(screen)}
    <section class="panel">
      <h2>Ожидание игроков</h2>
      <p>Код партии: <code class="big">${e(details.gameId)}</code>
        <button class="ghost" data-action="copy-link">Скопировать ссылку</button></p>
      <ol class="seats">${details.players
        .map(
          (p) => `<li>${e(p.name)}${p.id === details.hostPlayerId ? ' <span class="tag">хост</span>' : ''}${
            p.id === screen.seat.playerId ? ' <span class="tag me">вы</span>' : ''
          }</li>`,
        )
        .join('')}</ol>
      <p class="muted">${details.players.length}/${MAX_PLAYERS} игроков. Первым ходит хост, дальше — в порядке входа.</p>
      ${
        isHost
          ? `<button class="primary wide" data-action="start" ${enough ? '' : 'disabled'}>
               ${enough ? 'Начать игру' : 'Нужен хотя бы ещё один игрок'}</button>`
          : '<p>Ждём, пока хост начнёт игру…</p>'
      }
    </section>
  </main>`;
}

// ---------- Game table ----------

export function tableView(screen: GameScreen): string {
  const state = screen.details!.state!;
  const myId = screen.seat.playerId;
  return `
  <main class="page">
    ${topBar(screen)}
    <section class="panel status">${statusView(state, screen)}</section>
    <section class="players">${state.players.map((p) => playerView(p, state, myId)).join('')}</section>
    <div class="columns">
      <section class="panel">
        <h2>Банк</h2>
        <div class="market">${marketView(state, myId)}</div>
      </section>
      <section class="panel log">
        <h2>Журнал</h2>
        <ol reversed>${screen.log
          .slice(-60)
          .reverse()
          .map((line) => `<li class="${line.mine ? 'mine' : ''}">${e(line.text)}</li>`)
          .join('')}</ol>
      </section>
    </div>
  </main>`;
}

function topBar(screen: GameScreen): string {
  return `
  <header class="topbar">
    <span class="logo-small">🎲 Machi Koro</span>
    <code>${e(screen.seat.gameId)}</code>
    <span class="grow"></span>
    <span class="conn ${screen.connected ? 'ok' : 'bad'}">${screen.connected ? '● онлайн' : '● нет связи'}</span>
    <span>${e(screen.seat.name)}</span>
    <button class="ghost" data-action="leave">Выйти</button>
  </header>`;
}

function nameOf(state: GameView, playerId: string): string {
  return state.players.find((p) => p.id === playerId)?.name ?? '?';
}

function statusView(state: GameView, screen: GameScreen): string {
  const me = state.players.find((p) => p.id === screen.seat.playerId);
  const myTurn = state.activePlayerId === screen.seat.playerId;
  const dice = state.turn.dice;
  const diceHtml = dice.length
    ? `<span class="dice">${dice.map((d) => `<span class="die">${dieFace(d)}</span>`).join('')}
         <span class="sum">= ${dice.reduce((a, b) => a + b, 0)}</span></span>`
    : '';

  if (state.phase === 'GAME_OVER') {
    const winner = state.winnerId ? nameOf(state, state.winnerId) : '?';
    const iWon = state.winnerId === screen.seat.playerId;
    return `<div class="banner">${iWon ? '🏆 Вы победили!' : `🏆 Победил(а) ${e(winner)}`}</div>
            <button class="primary" data-action="leave">На главную</button>`;
  }

  const turnLine = myTurn
    ? `<strong>Ваш ход</strong> — ${PHASE_TEXT[state.phase]}`
    : `Ходит <strong>${e(nameOf(state, state.activePlayerId))}</strong> — ${PHASE_TEXT[state.phase]}`;
  const actions = myTurn && me ? actionsView(state, me, screen) : '';
  return `<div class="row between wrap"><div>${turnLine}</div>${diceHtml}</div>${actions}`;
}

function actionsView(state: GameView, me: PlayerView, screen: GameScreen): string {
  const train = me.landmarks.includes('TRAIN_STATION');
  switch (state.phase) {
    case 'ROLL':
      return `<div class="actions">
        <button class="primary" data-action="roll" data-dice="1">Бросить 1 кубик</button>
        ${train ? '<button class="primary" data-action="roll" data-dice="2">Бросить 2 кубика</button>' : ''}
      </div>`;
    case 'REROLL_DECISION':
      return `<div class="actions">
        <span class="hint">Radio Tower: можно перебросить один раз.</span>
        <button class="primary" data-action="keep">Оставить</button>
        <button data-action="reroll" data-dice="1">Перебросить 1 кубик</button>
        ${train ? '<button data-action="reroll" data-dice="2">Перебросить 2 кубика</button>' : ''}
      </div>`;
    case 'AWAITING_TARGET':
      return targetView(state, me, screen);
    case 'BUILD':
      return `<div class="actions">
        <span class="hint">Купите одну карту в банке или достопримечательность в своём городе — это завершит ход.</span>
        <button data-action="pass">Ничего не строить</button>
      </div>`;
    default:
      return '';
  }
}

function targetView(state: GameView, me: PlayerView, screen: GameScreen): string {
  const card = state.turn.pendingTargets[0];
  const opponents = state.players.filter((p) => p.id !== me.id);
  if (card === 'TV_STATION') {
    return `<div class="actions">
      <span class="hint">TV Station: у кого забрать до 5 монет?</span>
      ${opponents
        .map((p) => `<button class="primary" data-action="tv" data-target="${e(p.id)}">${e(p.name)} (${p.coins} 🪙)</button>`)
        .join('')}
    </div>`;
  }
  const swap = screen.swap;
  const target = opponents.find((p) => p.id === swap.targetId);
  const options = (owner: PlayerView | undefined, selected?: Establishment) =>
    owner
      ? ESTABLISHMENT_ORDER.filter((c) => (owner.establishments[c] ?? 0) > 0 && !isPurple(c))
          .map((c) => `<option value="${c}" ${c === selected ? 'selected' : ''}>${ESTABLISHMENTS[c].name}</option>`)
          .join('')
      : '';
  const ready = swap.give && target && swap.take;
  return `<div class="actions swap">
    <span class="hint">Business Center: обменяйте своё предприятие на чужое (фиолетовые нельзя).</span>
    <label>Отдать <select data-swap="give"><option value="">—</option>${options(me, swap.give)}</select></label>
    <label>Игрок <select data-swap="targetId"><option value="">—</option>${opponents
      .map((p) => `<option value="${e(p.id)}" ${p.id === swap.targetId ? 'selected' : ''}>${e(p.name)}</option>`)
      .join('')}</select></label>
    <label>Забрать <select data-swap="take" ${target ? '' : 'disabled'}><option value="">—</option>${options(target, swap.take)}</select></label>
    <button class="primary" data-action="swap" ${ready ? '' : 'disabled'}>Обменять</button>
    <button data-action="skip-swap">Не обменивать</button>
  </div>`;
}

function playerView(player: PlayerView, state: GameView, myId: string): string {
  const mine = player.id === myId;
  const active = player.id === state.activePlayerId;
  const canBuild = mine && active && state.phase === 'BUILD';
  const landmarks = LANDMARK_ORDER.map((l) => {
    const info = LANDMARKS[l];
    const built = player.landmarks.includes(l);
    const title = `${info.name} (${info.cost} 🪙): ${info.effect}`;
    if (canBuild && !built) {
      return `<button class="landmark" title="${e(title)}" data-action="build-landmark" data-landmark="${l}"
        ${player.coins >= info.cost ? '' : 'disabled'}>${info.name} · ${info.cost}🪙</button>`;
    }
    return `<span class="landmark ${built ? 'built' : ''}" title="${e(title)}">${built ? '✔ ' : ''}${info.name}</span>`;
  }).join('');
  const city = ESTABLISHMENT_ORDER.filter((c) => (player.establishments[c] ?? 0) > 0)
    .map((c) => {
      const info = ESTABLISHMENTS[c];
      const count = player.establishments[c] ?? 0;
      return `<span class="chip ${info.color.toLowerCase()}" title="${e(`${numbersLabel(c)}: ${info.effect}`)}">
        <b>${numbersLabel(c)}</b> ${ICONS[info.icon]} ${info.name}${count > 1 ? ` ×${count}` : ''}</span>`;
    })
    .join('');
  return `
  <article class="player ${active ? 'active' : ''} ${mine ? 'mine' : ''}">
    <header>
      <span class="name">${e(player.name)}${mine ? ' <span class="tag me">вы</span>' : ''}</span>
      <span class="coins" title="${coins(player.coins)}">🪙 ${player.coins}</span>
    </header>
    <div class="landmarks">${landmarks}</div>
    <div class="city">${city}</div>
  </article>`;
}

function marketView(state: GameView, myId: string): string {
  const me = state.players.find((p) => p.id === myId);
  const canBuild = !!me && state.activePlayerId === myId && state.phase === 'BUILD';
  return ESTABLISHMENT_ORDER.map((c) => {
    const info = ESTABLISHMENTS[c];
    const left = state.supply[c] ?? 0;
    const ownedPurple = isPurple(c) && (me?.establishments[c] ?? 0) > 0;
    const affordable = !!me && me.coins >= info.cost;
    const button = canBuild
      ? `<button class="buy" data-action="build" data-card="${c}"
          ${affordable && left > 0 && !ownedPurple ? '' : 'disabled'}>Купить · ${info.cost}🪙</button>`
      : `<span class="cost">${info.cost}🪙</span>`;
    return `
    <div class="card ${info.color.toLowerCase()} ${left === 0 ? 'sold-out' : ''}">
      <div class="card-head"><b class="numbers">${numbersLabel(c)}</b><span>${ICONS[info.icon]}</span></div>
      <div class="card-name">${info.name}</div>
      <div class="card-effect">${info.effect}<br><small>${COLOR_NAMES[info.color]}</small></div>
      <div class="card-foot"><small>${left > 0 ? `осталось ${left}` : 'нет в банке'}</small>${button}</div>
    </div>`;
  }).join('');
}
