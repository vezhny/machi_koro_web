import './style.css';
import { ApiError, api } from './api';
import { GameConnection } from './connection';
import type { Command, Establishment, GameDetails, GameUpdate, Landmark, LobbyView } from './protocol';
import type { GameScreen, Screen } from './state';
import {
  currentSeat,
  forgetSeat,
  saveName,
  saveSeat,
  savedName,
  seatsFor,
  setCurrentSeat,
  type Seat,
} from './storage';
import { describeEvent, errorText } from './text';
import { homeView, lobbyListView, pickSeatView, tableView, waitingRoomView } from './views';

const LOBBY_POLL_MS = 5000;
const TOAST_MS = 4000;

const root = document.getElementById('app')!;
const toastElement = document.getElementById('toast')!;

let screen: Screen = { kind: 'home', lobbies: [] };
let connection: GameConnection | null = null;
let lobbyTimer: number | undefined;
let toastTimer: number | undefined;

// ---------- Rendering ----------

function render(): void {
  switch (screen.kind) {
    case 'home':
      root.innerHTML = homeView(screen, savedName());
      break;
    case 'pick':
      root.innerHTML = pickSeatView(screen, savedName());
      break;
    case 'game':
      if (!screen.details) {
        root.innerHTML = '<main class="page narrow"><p class="muted">Загрузка партии…</p></main>';
      } else if (screen.details.state) {
        root.innerHTML = tableView(screen);
      } else {
        root.innerHTML = waitingRoomView(screen);
      }
      break;
  }
}

function toast(message: string): void {
  toastElement.textContent = message;
  toastElement.classList.add('visible');
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => toastElement.classList.remove('visible'), TOAST_MS);
}

function failure(error: unknown): string {
  return error instanceof ApiError ? errorText(error) : 'Что-то пошло не так';
}

// ---------- Navigation ----------

function route(): void {
  connection?.close();
  connection = null;
  window.clearInterval(lobbyTimer);
  const match = /^#\/game\/([A-Za-z0-9]+)$/.exec(window.location.hash);
  if (match) {
    void openGame(match[1].toUpperCase());
  } else {
    openHome();
  }
}

function goHome(error?: string): void {
  if (window.location.hash === '' || window.location.hash === '#/') {
    route();
  } else {
    window.location.hash = '#/';
  }
  if (error && screen.kind === 'home') {
    screen.error = error;
    render();
  }
}

function goToGame(gameId: string): void {
  window.location.hash = `#/game/${gameId}`;
}

function openHome(): void {
  screen = { kind: 'home', lobbies: [] };
  render();
  void refreshLobbies();
  lobbyTimer = window.setInterval(() => void refreshLobbies(), LOBBY_POLL_MS);
}

async function refreshLobbies(): Promise<void> {
  const home = screen;
  if (home.kind !== 'home') return;
  try {
    home.lobbies = await api.listLobbies();
  } catch {
    return;
  }
  // Only the list is redrawn so typing in the inputs is not interrupted.
  const list = document.getElementById('lobby-list');
  if (screen === home && list) {
    list.innerHTML = lobbyListView(home.lobbies);
  }
}

async function openGame(gameId: string): Promise<void> {
  const seat = currentSeat(gameId);
  if (seat) {
    enterGame(seat);
    return;
  }
  try {
    const details = await api.getGame(gameId);
    const seats = seatsFor(gameId).filter((s) => details.players.some((p) => p.id === s.playerId));
    screen = { kind: 'pick', gameId, details, seats };
    render();
  } catch (error) {
    goHome(failure(error));
  }
}

// ---------- Game session ----------

function enterGame(seat: Seat): void {
  setCurrentSeat(seat, seat.gameId);
  const game: GameScreen = { kind: 'game', seat, details: null, log: [], connected: false, swap: {} };
  screen = game;
  render();
  connection = new GameConnection(seat, {
    onUpdate: (update) => applyUpdate(game, update),
    onLobby: (lobby) => applyLobby(game, lobby),
    onError: (error) => {
      if (screen !== game) return;
      toast(errorText(error));
      if (error.code === 'STALE_VERSION') void resync(game);
    },
    onConnected: () => {
      if (screen !== game) return;
      game.connected = true;
      void resync(game);
    },
    onDisconnected: (reason) => {
      if (screen !== game) return;
      game.connected = false;
      render();
      // A rejected CONNECT usually means the server forgot the game (restart): check over REST.
      if (reason) void resync(game);
    },
  });
  void resync(game);
}

/** Fetches the full state over REST; used on start, on every (re)connect and after errors. */
async function resync(game: GameScreen): Promise<void> {
  try {
    const details = await api.getGame(game.seat.gameId);
    if (screen !== game) return;
    if (!details.players.some((p) => p.id === game.seat.playerId)) {
      throw new ApiError('INVALID_TOKEN', 'Seat not found', 404);
    }
    mergeDetails(game, details);
    render();
  } catch (error) {
    if (screen !== game) return;
    if (error instanceof ApiError && error.status === 404) {
      forgetSeat(game.seat.playerId);
      goHome('Партия не найдена — возможно, сервер перезапускался.');
    } else {
      toast(failure(error));
    }
  }
}

function mergeDetails(game: GameScreen, details: GameDetails): void {
  const known = game.details?.state;
  if (known && details.state && details.state.version < known.version) {
    // A broadcast already brought something newer; keep it, refresh only the lobby part.
    game.details = { ...details, state: known };
  } else {
    game.details = details;
  }
}

function applyUpdate(game: GameScreen, update: GameUpdate): void {
  if (screen !== game || !game.details) return;
  const known = game.details.state;
  if (known && update.state.version <= known.version) return;
  if (known?.phase !== update.state.phase || known?.activePlayerId !== update.state.activePlayerId) {
    game.swap = {};
  }
  game.details = {
    ...game.details,
    status: update.state.phase === 'GAME_OVER' ? 'FINISHED' : 'IN_PROGRESS',
    state: update.state,
  };
  const name = (id: string) => update.state.players.find((p) => p.id === id)?.name ?? '?';
  for (const event of update.events) {
    const text = describeEvent(event, name);
    if (text) {
      game.log.push({ text, mine: 'playerId' in event && event.playerId === game.seat.playerId });
    }
  }
  render();
}

function applyLobby(game: GameScreen, lobby: LobbyView): void {
  if (screen !== game) return;
  game.details = { ...lobby, state: game.details?.state ?? null };
  render();
}

function send(build: (version: number) => Command): void {
  const game = screen;
  if (game.kind !== 'game' || !game.details?.state) return;
  if (!connection?.send(build(game.details.state.version))) {
    toast('Нет связи с сервером — переподключаемся…');
  }
}

// ---------- Joining ----------

function readName(): string | null {
  const input = document.getElementById('name') as HTMLInputElement | null;
  const name = input?.value.trim() ?? savedName();
  if (!name) {
    toast('Введите имя');
    input?.focus();
    return null;
  }
  saveName(name);
  return name;
}

async function takeSeat(request: Promise<{ gameId: string; playerId: string; playerToken: string }>,
                        name: string): Promise<Seat | null> {
  try {
    const joined = await request;
    const seat: Seat = { ...joined, name };
    saveSeat(seat);
    setCurrentSeat(seat, seat.gameId);
    return seat;
  } catch (error) {
    toast(failure(error));
    return null;
  }
}

// ---------- Actions ----------

async function handleAction(action: string, data: DOMStringMap): Promise<void> {
  switch (action) {
    case 'create': {
      const name = readName();
      const seat = name && (await takeSeat(api.createGame(name), name));
      if (seat) goToGame(seat.gameId);
      return;
    }
    case 'join-lobby': {
      const name = readName();
      const seat = name && (await takeSeat(api.joinGame(data.game!, name), name));
      if (seat) goToGame(seat.gameId);
      return;
    }
    case 'refresh-lobbies':
      return refreshLobbies();
    case 'home':
      return goHome();
    case 'pick-seat': {
      if (screen.kind !== 'pick') return;
      const seat = screen.seats.find((s) => s.playerId === data.player);
      if (seat) enterGame(seat);
      return;
    }
    case 'join-new': {
      if (screen.kind !== 'pick') return;
      const name = readName();
      const seat = name && (await takeSeat(api.joinGame(screen.gameId, name), name));
      if (seat) enterGame(seat);
      return;
    }
    case 'copy-link':
      try {
        await navigator.clipboard.writeText(window.location.href);
        toast('Ссылка скопирована');
      } catch {
        toast(window.location.href);
      }
      return;
    case 'leave':
      if (screen.kind === 'game') setCurrentSeat(null, screen.seat.gameId);
      return goHome();
    case 'start': {
      const game = screen;
      if (game.kind !== 'game') return;
      try {
        mergeDetails(game, await api.startGame(game.seat.gameId, game.seat.playerToken));
        if (screen === game) render();
      } catch (error) {
        toast(failure(error));
      }
      return;
    }
    case 'roll':
      return send((v) => ({ type: 'RollDice', expectedVersion: v, diceCount: data.dice === '2' ? 2 : 1 }));
    case 'reroll':
      return send((v) => ({ type: 'Reroll', expectedVersion: v, diceCount: data.dice === '2' ? 2 : 1 }));
    case 'keep':
      return send((v) => ({ type: 'KeepRoll', expectedVersion: v }));
    case 'tv':
      return send((v) => ({ type: 'ChooseTarget', expectedVersion: v, targetPlayerId: data.target! }));
    case 'swap': {
      if (screen.kind !== 'game') return;
      const { give, targetId, take } = screen.swap;
      return send((v) => ({ type: 'ChooseTarget', expectedVersion: v, targetPlayerId: targetId!, give, take }));
    }
    case 'skip-swap':
      return send((v) => ({ type: 'ChooseTarget', expectedVersion: v, targetPlayerId: null }));
    case 'build':
      return send((v) => ({ type: 'Build', expectedVersion: v, establishment: data.card as Establishment }));
    case 'build-landmark':
      return send((v) => ({ type: 'Build', expectedVersion: v, landmark: data.landmark as Landmark }));
    case 'pass':
      return send((v) => ({ type: 'Pass', expectedVersion: v }));
  }
}

root.addEventListener('click', (event) => {
  const target = (event.target as Element).closest<HTMLElement>('[data-action]');
  if (!target || target.hasAttribute('disabled')) return;
  event.preventDefault();
  void handleAction(target.dataset.action!, target.dataset);
});

root.addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.target as HTMLFormElement;
  if (form.dataset.form !== 'join-code') return;
  const code = String(new FormData(form).get('code') ?? '').trim().toUpperCase();
  if (!code) return;
  if (seatsFor(code).length > 0) {
    goToGame(code);
    return;
  }
  const name = readName();
  if (!name) return;
  try {
    const seat = await takeSeat(api.joinGame(code, name), name);
    if (seat) goToGame(seat.gameId);
  } catch {
    // takeSeat already reported the error
  }
});

root.addEventListener('change', (event) => {
  const select = event.target as HTMLSelectElement;
  const field = select.dataset.swap as keyof GameScreen['swap'] | undefined;
  if (!field || screen.kind !== 'game') return;
  const value = select.value || undefined;
  if (field === 'targetId') {
    screen.swap = { ...screen.swap, targetId: value, take: undefined };
  } else {
    screen.swap = { ...screen.swap, [field]: value as Establishment | undefined };
  }
  render();
});

window.addEventListener('hashchange', route);
route();
