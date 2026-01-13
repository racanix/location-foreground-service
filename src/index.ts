import { registerPlugin } from '@capacitor/core';

import type { LocationForegroundServicePlugin } from './definitions';

const LocationForegroundService = registerPlugin<LocationForegroundServicePlugin>('LocationForegroundService', {
  web: () => import('./web').then((m) => new m.LocationForegroundServiceWeb()),
});

export * from './definitions';
export { LocationForegroundService };
