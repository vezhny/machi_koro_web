import type { Establishment, GameDetails, LobbySummary } from './protocol';
import type { Seat } from './storage';

export interface HomeScreen {
  kind: 'home';
  lobbies: LobbySummary[];
  error?: string;
}

/** Opened a game link in a tab that does not play in that game yet. */
export interface PickSeatScreen {
  kind: 'pick';
  gameId: string;
  details: GameDetails;
  /** stored seats of this browser that belong to the game */
  seats: Seat[];
}

export interface LogLine {
  text: string;
  mine: boolean;
}

/** Business Center choices in progress (before the swap is sent). */
export interface SwapDraft {
  give?: Establishment;
  targetId?: string;
  take?: Establishment;
}

export interface GameScreen {
  kind: 'game';
  seat: Seat;
  /** null until the first REST/STOMP response */
  details: GameDetails | null;
  /** newest last */
  log: LogLine[];
  connected: boolean;
  swap: SwapDraft;
}

export type Screen = HomeScreen | PickSeatScreen | GameScreen;
