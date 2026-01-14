import { WebPlugin } from '@capacitor/core';

import type {
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
}
