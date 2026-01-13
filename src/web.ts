import { WebPlugin } from '@capacitor/core';

import type { LocationForegroundServicePlugin } from './definitions';

export class LocationForegroundServiceWeb extends WebPlugin implements LocationForegroundServicePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async print(options: {value: string}): Promise<{ value: string }> {
    console.log('PRINT', options);
    return options;
  }
}
