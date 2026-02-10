export type LocationAccuracy = 'high' | 'balanced';

/**
 * Tipos de alertas soportadas por el sistema.
 */
export enum AlertType {
  JOURNEY = 'JOURNEY',
  QUICK = 'QUICK',
  REQUEST_LOCATION = 'REQUEST_LOCATION',
  DEFAULT = 'DEFAULT',
}

/**
 * Ubicación objetivo para detener el servicio cuando el usuario llegue al destino.
 */
export interface TargetLocation {
  /** Latitud del destino. */
  latitude: number;
  /** Longitud del destino. */
  longitude: number;
  /** Rango en metros para considerar llegada. Valor por defecto: 10. */
  range?: number;
}

/**
 * Representa una alerta activa que el servicio debe monitorear.
 */
export interface Alert {
  /** Identificador único de la alerta. */
  id: string;
  /** Tipo de alerta. */
  type: AlertType;
  /** Ubicación objetivo asociada a esta alerta, si aplica. */
  targetLocation?: TargetLocation;
}

/**
 * Configuración requerida para iniciar el servicio nativo de rastreo.
 * Todas las propiedades son multiplataforma y deben mantenerse sincronizadas con Android/iOS.
 */
export interface StartTrackingOptions {
  /** URL absoluta del endpoint que recibirá las posiciones. */
  endpoint: string;
  /** URL completa del endpoint para finalizar la alerta */
  alertTerminationEndpoint?: string;
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

  /**
   * Agrega una nueva alerta al sistema de monitoreo.
   * Retorna true si se agregó exitosamente, false si ya existía.
   */
  addAlert(alert: Alert): Promise<{ success: boolean }>;

  /**
   * Remueve una alerta existente del sistema de monitoreo.
   * Retorna true si se removió exitosamente, false si no existía.
   */
  removeAlert(options: { id: string }): Promise<{ success: boolean }>;

  /**
   * Verifica si existe una alerta con el ID proporcionado.
   * Retorna el objeto de alerta si existe, o null en caso contrario.
   */
  existsAlert(options: { id: string }): Promise<{ alert: Alert | null }>;

  /**
   * Obtiene todas las alertas activas en el sistema.
   * Retorna una lista de objetos de alerta.
   */
  getAllAlerts(): Promise<{ alerts: Alert[] }>;

  /**
   * Verifica si existe al menos una alerta del tipo especificado.
   * Retorna true si existe, false en caso contrario.
   */
  existAlertType(options: { type: AlertType }): Promise<{ exists: boolean }>;

  /**
   * Obtiene el número total de alertas activas en el sistema.
   * Retorna el conteo como un número entero.
   */
  getAlertCount(): Promise<{ count: number }>;

  /**
   * Remueve todas las alertas activas del sistema.
   * Retorna true si se removieron exitosamente.
   */
  clearAllAlerts(): Promise<{ success: boolean }>;
}
