import { Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/format';
import { Operator, ScanResult } from '../../core/models';

interface DetectedBarcode {
  rawValue: string;
}

interface BarcodeDetectorLike {
  detect(source: HTMLVideoElement): Promise<DetectedBarcode[]>;
}

declare const BarcodeDetector: { new (options: { formats: string[] }): BarcodeDetectorLike } | undefined;

/**
 * Registro de lecturas desde la web para supervisores (respaldo de la app Android). Usa la cámara con la API
 * BarcodeDetector cuando el navegador la soporta; si no, se pega el contenido del QR.
 */
@Component({
  selector: 'app-scan',
  imports: [FormsModule],
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Registrar lectura</h1>
          <p class="muted">Cada envío lleva un identificador único: si se reenvía, el servidor responde DUPLICATE y no registra dos veces.</p>
        </div>
      </div>
      <div class="grid grid-2">
        <form class="card stack" (ngSubmit)="submit()">
          <label>Operario
            <select name="operator" [(ngModel)]="operatorCode" required>
              @for (o of operators(); track o.id) { <option [value]="o.code">{{ o.code }} · {{ o.fullName }}</option> }
            </select>
          </label>
          <label>Contenido del QR
            <textarea name="payload" rows="3" required [(ngModel)]="payload" placeholder="TT1.k1.…"></textarea>
          </label>
          <div class="row">
            @if (cameraSupported) {
              <button type="button" (click)="toggleCamera()">{{ scanning() ? 'Detener cámara' : 'Escanear con cámara' }}</button>
            }
            <button class="primary" type="submit" [disabled]="!payload || !operatorCode">Registrar</button>
          </div>
          @if (result(); as r) {
            <div class="message" [class]="r.status === 'ACCEPTED' || r.status === 'DUPLICATE' ? 'message success' : 'message error'">
              <strong>{{ r.status }}</strong> · {{ r.message }}
              @if (r.bundleCode) { <br />{{ r.bundleCode }} · {{ r.operationCode }} · {{ r.quantity }} piezas }
            </div>
          }
          @if (error()) { <div class="message error">{{ error() }}</div> }
        </form>
        <div class="card camera" [hidden]="!scanning()">
          <video #video playsinline muted></video>
        </div>
      </div>
    </section>
  `,
  styles: `
    video {
      width: 100%;
      border-radius: 8px;
      background: #000;
    }
  `,
})
export class Scan implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private stream: MediaStream | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly cameraSupported = typeof BarcodeDetector !== 'undefined' && !!navigator.mediaDevices;
  protected readonly operators = signal<Operator[]>([]);
  protected readonly result = signal<ScanResult | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly scanning = signal(false);
  protected operatorCode = '';
  protected payload = '';

  constructor() {
    this.api.operators().subscribe((ops) => {
      this.operators.set(ops);
      this.operatorCode = ops[0]?.code ?? '';
    });
  }

  protected submit(): void {
    this.error.set(null);
    this.api
      .scan({
        clientReadingId: crypto.randomUUID(),
        payload: this.payload.trim(),
        operatorCode: this.operatorCode,
        scannedAt: new Date().toISOString(),
        deviceId: 'web',
      })
      .subscribe({
        next: (result) => this.result.set(result),
        error: (e) => {
          const body = (e as { error?: ScanResult }).error;
          if (body?.status) {
            this.result.set(body);
          } else {
            this.error.set(errorMessage(e));
          }
        },
      });
  }

  protected async toggleCamera(): Promise<void> {
    if (this.scanning()) {
      this.stopCamera();
      return;
    }
    const element = this.video()?.nativeElement;
    if (!element || typeof BarcodeDetector === 'undefined') {
      return;
    }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      element.srcObject = this.stream;
      await element.play();
      this.scanning.set(true);
      const detector = new BarcodeDetector({ formats: ['qr_code'] });
      this.timer = setInterval(async () => {
        const codes = await detector.detect(element);
        if (codes.length) {
          this.payload = codes[0].rawValue;
          this.stopCamera();
          this.submit();
        }
      }, 400);
    } catch {
      this.error.set('No se pudo abrir la cámara');
    }
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }

  private stopCamera(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
    this.scanning.set(false);
  }
}
