import type { UIRouterReact } from '@uirouter/react';
import type { ILogService, IQService } from 'angular';
import * as React from 'react';

import { DirectProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import type { CancellableTimeout } from '../utils/cancellableTimeout';
import { createCancellableTimeout } from '../utils/cancellableTimeout';
import { createDiagnosticLogger } from '../utils/diagnosticLogger';
import { interpolate } from '../utils/interpolate';
import { createNativePromiseService } from '../utils/nativePromiseService';

export interface DeckRuntime {
  router: UIRouterReact | null;
  promiseService: IQService;
  timeoutService: CancellableTimeout;
  logger: ILogService;
  interpolate: typeof interpolate;
  providerServiceDelegate: DirectProviderServiceDelegate;
  dispose: () => void;
}

export const DeckRuntimeContext = React.createContext<DeckRuntime | null>(null);

export function createDeckRuntime(router: UIRouterReact | null = null): DeckRuntime {
  const promiseService = createNativePromiseService();
  const timeoutService = createCancellableTimeout();

  return {
    router,
    promiseService,
    timeoutService,
    logger: createDiagnosticLogger(),
    interpolate,
    providerServiceDelegate: new DirectProviderServiceDelegate(promiseService),
    dispose: timeoutService.dispose,
  };
}
