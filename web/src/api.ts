import type { ErrorMessage, GameDetails, JoinResponse, LobbySummary } from './protocol';

export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

async function request<T>(method: string, path: string, body?: unknown, token?: string): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers['X-Player-Token'] = token;
  }
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError('NETWORK', 'Сервер недоступен', 0);
  }
  if (!response.ok) {
    let error: ErrorMessage = { code: 'HTTP_' + response.status, message: response.statusText };
    try {
      error = (await response.json()) as ErrorMessage;
    } catch {
      // Non-JSON error body: keep the HTTP status.
    }
    throw new ApiError(error.code, error.message, response.status);
  }
  return (await response.json()) as T;
}

export const api = {
  listLobbies: () => request<LobbySummary[]>('GET', '/api/games'),
  createGame: (name: string) => request<JoinResponse>('POST', '/api/games', { name }),
  joinGame: (gameId: string, name: string) =>
    request<JoinResponse>('POST', `/api/games/${encodeURIComponent(gameId)}/join`, { name }),
  startGame: (gameId: string, token: string) =>
    request<GameDetails>('POST', `/api/games/${encodeURIComponent(gameId)}/start`, undefined, token),
  getGame: (gameId: string) => request<GameDetails>('GET', `/api/games/${encodeURIComponent(gameId)}`),
};
