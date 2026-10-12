import { ESTABLISHMENTS, LANDMARKS } from './cards';
import type { ErrorMessage, GameEvent, Phase } from './protocol';

export function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

export function plural(n: number, one: string, few: string, many: string): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

export function coins(n: number): string {
  return `${n} ${plural(n, 'монета', 'монеты', 'монет')}`;
}

export const PHASE_TEXT: Record<Phase, string> = {
  ROLL: 'бросок кубиков',
  REROLL_DECISION: 'решение о перебросе',
  AWAITING_TARGET: 'выбор цели',
  BUILD: 'стройка',
  GAME_OVER: 'игра окончена',
};

const ERROR_TEXT: Record<string, string> = {
  NOT_YOUR_TURN: 'Сейчас не ваш ход',
  STALE_VERSION: 'Состояние обновилось — повторите действие',
  WRONG_PHASE: 'Сейчас это действие недоступно',
  NOT_ENOUGH_COINS: 'Не хватает монет',
  SUPPLY_EMPTY: 'Эта карта закончилась в банке',
  PURPLE_ALREADY_OWNED: 'Каждую фиолетовую карту можно иметь только в одном экземпляре',
  LANDMARK_ALREADY_BUILT: 'Уже построено',
  TRAIN_STATION_REQUIRED: 'Для двух кубиков нужен Train Station',
  INVALID_DICE_COUNT: 'Можно бросить 1 или 2 кубика',
  INVALID_TARGET: 'Выберите соперника',
  INVALID_SWAP: 'Выберите для обмена карты, которые есть у игроков (не фиолетовые)',
  GAME_OVER: 'Игра окончена',
  GAME_NOT_STARTED: 'Игра ещё не началась',
  GAME_FULL: 'В партии уже 4 игрока',
  GAME_ALREADY_STARTED: 'Партия уже началась',
  GAME_NOT_FOUND: 'Партия не найдена',
  NOT_ENOUGH_PLAYERS: 'Нужно минимум 2 игрока',
  NOT_HOST: 'Начать игру может только создатель партии',
  INVALID_TOKEN: 'Место в партии не найдено',
  BAD_REQUEST: 'Некорректный запрос',
  NETWORK: 'Сервер недоступен',
};

export function errorText(error: ErrorMessage): string {
  return ERROR_TEXT[error.code] ?? error.message;
}

const DICE_FACES = ['⚀', '⚁', '⚂', '⚃', '⚄', '⚅'];

export function dieFace(value: number): string {
  return DICE_FACES[value - 1] ?? String(value);
}

/** One log line per event; {@code name} resolves player ids. */
export function describeEvent(event: GameEvent, name: (playerId: string) => string): string | null {
  const card = (c: keyof typeof ESTABLISHMENTS) => ESTABLISHMENTS[c].name;
  switch (event.type) {
    case 'DiceRolled': {
      const sum = event.dice.reduce((a, b) => a + b, 0);
      const verb = event.reroll ? 'перебросил(а)' : 'бросил(а)';
      return `${name(event.playerId)} ${verb}: ${event.dice.map(dieFace).join(' ')} = ${sum}`;
    }
    case 'IncomeReceived':
      return `${name(event.playerId)} +${coins(event.amount)} (${card(event.card)})`;
    case 'CoinsTransferred':
      return `${name(event.fromPlayerId)} → ${name(event.toPlayerId)}: ${coins(event.amount)} (${card(event.card)})`;
    case 'TargetRequired':
      return `${name(event.playerId)} выбирает цель для ${card(event.card)}`;
    case 'EstablishmentsSwapped':
      return `${name(event.playerId)} обменял(а) ${card(event.given)} на ${card(event.taken)} у ${name(event.targetPlayerId)}`;
    case 'Built': {
      const what = event.target.kind === 'ESTABLISHMENT'
        ? card(event.target.establishment)
        : LANDMARKS[event.target.landmark].name;
      return `${name(event.playerId)} построил(а) ${what}`;
    }
    case 'TurnPassed':
      return `Ход переходит к ${name(event.toPlayerId)}`;
    case 'ExtraTurnGranted':
      return `${name(event.playerId)} выбросил(а) дубль и ходит ещё раз`;
    case 'GameWon':
      return `🏆 ${name(event.playerId)} построил(а) все достопримечательности и победил(а)!`;
  }
}
