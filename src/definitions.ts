export type LocationAccuracy = 'high' | 'balanced';

/**
 * Configuración requerida para iniciar el servicio nativo de rastreo.
 * Todas las propiedades son multiplataforma y deben mantenerse sincronizadas con Android/iOS.
 */
export interface StartTrackingOptions {
  /** URL absoluta del endpoint que recibirá las posiciones. */
  endpoint: string;
  /** Encabezados adicionales enviados en cada petición HTTP. */
  headers?: Record<string, string>;
  /** Datos estáticos adjuntos a cada payload (por ejemplo, ids de usuario o dispositivo). */
  metadata?: Record<string, string>;
  /** Intervalo deseado entre lecturas (ms). Valor por defecto: 10000. */
  minUpdateIntervalMillis?: number;
  /** Intervalo mínimo absoluto aceptado por el servicio (ms). Valor por defecto: 5000. */
  fastestIntervalMillis?: number;
  /** Distancia mínima en metros para disparar un nuevo evento. Valor por defecto: 5. */
  minUpdateDistanceMeters?: number;
  /** Texto mostrado como título de la notificación persistente. */
  notificationTitle?: string;
  /** Texto mostrado como cuerpo de la notificación persistente. */
  notificationBody?: string;
  /** Tiempo de espera antes de reintentar envíos fallidos (ms). Valor por defecto: 5000. */
  retryDelayMillis?: number;
  /** Capacidad máxima de la cola en memoria. Valor por defecto: 32. */
  queueCapacity?: number;
  /** Prioridad deseada para el proveedor de ubicación. */
  accuracy?: LocationAccuracy;
}

/** Estado mínimo devuelto por los métodos públicos del plugin. */
export interface TrackingState {
  /** Indica si el servicio nativo está ejecutándose. */
  running: boolean;
}

export type StartTrackingResult = TrackingState;
export type StopTrackingResult = TrackingState;
export type IsTrackingResult = TrackingState;

export interface LocationForegroundServicePlugin {
  /** Inicia el servicio nativo y habilita el reporte continuo de ubicación. */
  startTracking(options: StartTrackingOptions): Promise<StartTrackingResult>;
  /** Detiene el servicio nativo y limpia los recursos asociados. */
  stopTracking(): Promise<StopTrackingResult>;
  /** Verifica si el servicio nativo sigue activo. */
  isTracking(): Promise<IsTrackingResult>;
}
