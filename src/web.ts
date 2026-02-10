import { WebPlugin } from '@capacitor/core';

import type {
  Alert,
  AlertType,
  IsTrackingResult,
  LocationForegroundServicePlugin,
  StartTrackingOptions,
  StartTrackingResult,
  StopTrackingResult,
} from './definitions';

export class LocationForegroundServiceWeb extends WebPlugin implements LocationForegroundServicePlugin {
  /**
   * La plataforma web no implementa rastreo persistente; se notifica al desarrollador.
   */
  async startTracking(_options: StartTrackingOptions): Promise<StartTrackingResult> {
    throw this.unimplemented('El servicio de ubicación persistente no está disponible en web.');
  }

  async stopTracking(): Promise<StopTrackingResult> {
    throw this.unimplemented('El servicio de ubicación persistente no está disponible en web.');
  }

  async isTracking(): Promise<IsTrackingResult> {
    return { running: false };
  }

  async addAlert(_alert: Alert): Promise<{ success: boolean }> {
    throw this.unimplemented('La gestión de alertas no está disponible en web.');
  }

  async removeAlert(_options: { id: string }): Promise<{ success: boolean }> {
    throw this.unimplemented('La gestión de alertas no está disponible en web.');
  }

  async existsAlert(_options: { id: string }): Promise<{ alert: Alert | null }> {
    throw this.unimplemented('La gestión de alertas no está disponible en web.');
  }

  async getAllAlerts(): Promise<{ alerts: Alert[] }> {
    throw this.unimplemented('La gestión de alertas no está disponible en web.');
  }

  async existAlertType(_options: { type: AlertType }): Promise<{ exists: boolean }> {
    throw this.unimplemented('La gestión de alertas no está disponible en web.');
  }

  async getAlertCount(): Promise<{ count: number }> {
    throw this.unimplemented('La gestión de alertas no está disponible en web.');
  }

  async clearAllAlerts(): Promise<{ success: boolean }> {
    throw this.unimplemented('La gestión de alertas no está disponible en web.');
  }
}
