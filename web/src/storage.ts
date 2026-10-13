// Seat credentials survive reloads and are shared by all tabs (localStorage). Which seat a
// particular tab plays is remembered per tab (sessionStorage), so two tabs of one browser can
// play against each other and each returns to its own seat after a reload.

export interface Seat {
  gameId: string;
  playerId: string;
  playerToken: string;
  name: string;
}

const SEATS_KEY = 'mk.seats';
const NAME_KEY = 'mk.name';
const CURRENT_PREFIX = 'mk.current.';

function read(storage: () => Storage, key: string): string | null {
  try {
    return storage().getItem(key);
  } catch {
    return null;
  }
}

function write(storage: () => Storage, key: string, value: string | null): void {
  try {
    if (value === null) {
      storage().removeItem(key);
    } else {
      storage().setItem(key, value);
    }
  } catch {
    // Storage unavailable (private mode, blocked cookies): the game still works until reload.
  }
}

const local = () => window.localStorage;
const session = () => window.sessionStorage;

function allSeats(): Seat[] {
  try {
    const parsed: unknown = JSON.parse(read(local, SEATS_KEY) ?? '[]');
    return Array.isArray(parsed) ? (parsed as Seat[]) : [];
  } catch {
    return [];
  }
}

export function saveSeat(seat: Seat): void {
  const seats = allSeats().filter((s) => s.playerId !== seat.playerId);
  seats.push(seat);
  write(local, SEATS_KEY, JSON.stringify(seats));
}

export function forgetSeat(playerId: string): void {
  write(local, SEATS_KEY, JSON.stringify(allSeats().filter((s) => s.playerId !== playerId)));
}

export function seatsFor(gameId: string): Seat[] {
  return allSeats().filter((s) => s.gameId === gameId);
}

/** Seat this tab plays in the given game, if any. */
export function currentSeat(gameId: string): Seat | undefined {
  const playerId = read(session, CURRENT_PREFIX + gameId);
  return seatsFor(gameId).find((s) => s.playerId === playerId);
}

export function setCurrentSeat(seat: Seat | null, gameId: string): void {
  write(session, CURRENT_PREFIX + gameId, seat ? seat.playerId : null);
}

export function savedName(): string {
  return read(local, NAME_KEY) ?? '';
}

export function saveName(name: string): void {
  write(local, NAME_KEY, name);
}
