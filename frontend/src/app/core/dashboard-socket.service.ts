import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { AuthService } from './auth.service';
import { DashboardSnapshot } from './models';
import { RUNTIME_CONFIG, webSocketUrl } from './runtime-config';

export type SocketState = 'connecting' | 'live' | 'offline';

/**
 * Suscripción STOMP a /topic/dashboard. El JWT viaja en el frame CONNECT (los navegadores no permiten
 * cabeceras en el handshake WebSocket). Reconecta sola y renueva el token antes de cada intento.
 */
@Injectable({ providedIn: 'root' })
export class DashboardSocketService {
  private readonly auth = inject(AuthService);
  private readonly url = webSocketUrl(inject(RUNTIME_CONFIG).apiUrl, window.location);
  private client: Client | null = null;

  readonly state = signal<SocketState>('offline');
  readonly snapshot = signal<DashboardSnapshot | null>(null);

  connect(destroyRef: DestroyRef): void {
    if (this.client?.active) {
      return;
    }
    this.state.set('connecting');
    const client = new Client({
      brokerURL: this.url,
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      beforeConnect: async (stomp) => {
        await this.auth.ensureAccessToken();
        stomp.connectHeaders = { Authorization: `Bearer ${this.auth.token() ?? ''}` };
      },
      onConnect: () => {
        this.state.set('live');
        client.subscribe('/topic/dashboard', (message: IMessage) => {
          this.snapshot.set(JSON.parse(message.body) as DashboardSnapshot);
        });
      },
      onStompError: () => {
        this.state.set('offline');
        void this.auth.refresh();
      },
      onWebSocketClose: () => this.state.set('offline'),
    });
    client.activate();
    this.client = client;
    destroyRef.onDestroy(() => this.disconnect());
  }

  disconnect(): void {
    void this.client?.deactivate();
    this.client = null;
    this.state.set('offline');
  }
}
