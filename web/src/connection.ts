import { Client, type IMessage } from '@stomp/stompjs';
import type { Command, ErrorMessage, GameUpdate, LobbyView } from './protocol';
import type { Seat } from './storage';

export interface ConnectionHandlers {
  onUpdate(update: GameUpdate): void;
  onLobby(lobby: LobbyView): void;
  onError(error: ErrorMessage): void;
  /** Called on every (re)connect after subscriptions are in place: time to resync over REST. */
  onConnected(): void;
  onDisconnected(reason?: string): void;
}

const RECONNECT_DELAY_MS = 2000;

function brokerUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

function parse<T>(message: IMessage): T {
  return JSON.parse(message.body) as T;
}

/** STOMP session of one seat; reconnects automatically. */
export class GameConnection {
  private readonly client: Client;

  constructor(
    private readonly seat: Seat,
    handlers: ConnectionHandlers,
  ) {
    const topic = `/topic/games/${seat.gameId}`;
    this.client = new Client({
      brokerURL: brokerUrl(),
      connectHeaders: { 'X-Player-Token': seat.playerToken },
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        this.client.subscribe(topic, (m) => handlers.onUpdate(parse<GameUpdate>(m)));
        this.client.subscribe(`${topic}/lobby`, (m) => handlers.onLobby(parse<LobbyView>(m)));
        this.client.subscribe('/user/queue/errors', (m) => handlers.onError(parse<ErrorMessage>(m)));
        handlers.onConnected();
      },
      onStompError: (frame) => handlers.onDisconnected(frame.headers['message']),
      onWebSocketClose: () => handlers.onDisconnected(),
    });
    this.client.activate();
  }

  get connected(): boolean {
    return this.client.connected;
  }

  /** @returns false if not connected; the command is not queued. */
  send(command: Command): boolean {
    if (!this.client.connected) {
      return false;
    }
    this.client.publish({
      destination: `/app/games/${this.seat.gameId}/commands`,
      body: JSON.stringify(command),
      headers: { 'content-type': 'application/json' },
    });
    return true;
  }

  close(): void {
    void this.client.deactivate();
  }
}
