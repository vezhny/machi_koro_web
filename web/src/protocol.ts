// Wire types, written by hand from docs/PROTOCOL.md. Keep both in sync.

export type Establishment =
  | 'WHEAT_FIELD'
  | 'RANCH'
  | 'BAKERY'
  | 'CAFE'
  | 'CONVENIENCE_STORE'
  | 'FOREST'
  | 'STADIUM'
  | 'TV_STATION'
  | 'BUSINESS_CENTER'
  | 'CHEESE_FACTORY'
  | 'FURNITURE_FACTORY'
  | 'MINE'
  | 'FAMILY_RESTAURANT'
  | 'APPLE_ORCHARD'
  | 'FRUIT_AND_VEGETABLE_MARKET';

export type Landmark = 'TRAIN_STATION' | 'SHOPPING_MALL' | 'AMUSEMENT_PARK' | 'RADIO_TOWER';

export type Phase = 'ROLL' | 'REROLL_DECISION' | 'AWAITING_TARGET' | 'BUILD' | 'GAME_OVER';

export type GameStatus = 'LOBBY' | 'IN_PROGRESS' | 'FINISHED';

// ---- REST ----

export interface JoinResponse {
  gameId: string;
  playerId: string;
  playerToken: string;
}

export interface PlayerSummary {
  id: string;
  name: string;
}

export interface LobbySummary {
  gameId: string;
  hostName: string;
  playerCount: number;
  maxPlayers: number;
}

export interface LobbyView {
  gameId: string;
  status: GameStatus;
  hostPlayerId: string;
  players: PlayerSummary[];
}

export interface GameDetails extends LobbyView {
  /** null while status is LOBBY */
  state: GameView | null;
}

export interface ErrorMessage {
  code: string;
  message: string;
}

// ---- Game state ----

export type CardCounts = Partial<Record<Establishment, number>>;

export interface PlayerView {
  id: string;
  name: string;
  coins: number;
  establishments: CardCounts;
  landmarks: Landmark[];
}

export interface TurnView {
  /** empty before the roll */
  dice: number[];
  rerollUsed: boolean;
  /** cards waiting for ChooseTarget; the first one is current */
  pendingTargets: Establishment[];
}

export interface GameView {
  gameId: string;
  version: number;
  phase: Phase;
  activePlayerId: string;
  /** in turn order */
  players: PlayerView[];
  /** sold-out cards are absent */
  supply: CardCounts;
  turn: TurnView;
  winnerId: string | null;
}

export type BuildTarget =
  | { kind: 'ESTABLISHMENT'; establishment: Establishment }
  | { kind: 'LANDMARK'; landmark: Landmark };

export type GameEvent =
  | { type: 'DiceRolled'; playerId: string; dice: number[]; reroll: boolean }
  | { type: 'IncomeReceived'; playerId: string; card: Establishment; amount: number }
  | { type: 'CoinsTransferred'; fromPlayerId: string; toPlayerId: string; card: Establishment; amount: number }
  | { type: 'TargetRequired'; playerId: string; card: Establishment }
  | { type: 'EstablishmentsSwapped'; playerId: string; targetPlayerId: string; given: Establishment; taken: Establishment }
  | { type: 'Built'; playerId: string; target: BuildTarget }
  | { type: 'TurnPassed'; fromPlayerId: string; toPlayerId: string }
  | { type: 'ExtraTurnGranted'; playerId: string }
  | { type: 'GameWon'; playerId: string };

/** Broadcast to /topic/games/{gameId}. */
export interface GameUpdate {
  state: GameView;
  events: GameEvent[];
}

// ---- Commands, sent to /app/games/{gameId}/commands ----

export type DiceCount = 1 | 2;

export type Command =
  | { type: 'RollDice'; expectedVersion: number; diceCount: DiceCount }
  | { type: 'Reroll'; expectedVersion: number; diceCount: DiceCount }
  | { type: 'KeepRoll'; expectedVersion: number }
  | {
      type: 'ChooseTarget';
      expectedVersion: number;
      /** null skips Business Center */
      targetPlayerId: string | null;
      give?: Establishment;
      take?: Establishment;
    }
  | { type: 'Build'; expectedVersion: number; establishment?: Establishment; landmark?: Landmark }
  | { type: 'Pass'; expectedVersion: number };
