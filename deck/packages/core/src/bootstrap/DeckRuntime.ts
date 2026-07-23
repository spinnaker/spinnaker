import type { UIRouterReact } from '@uirouter/react';
import type { ILogService, IQService } from 'angular';

import { DeckRuntimeServices } from './DeckRuntimeServices';
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
  services: DeckRuntimeServices;
  dispose: () => void;
}

export function createDeckRuntime(router: UIRouterReact | null = null): DeckRuntime {
  const promiseService = createNativePromiseService();
  const timeoutService = createCancellableTimeout();
  const logger = createDiagnosticLogger();
  const providerServiceDelegate = new DirectProviderServiceDelegate(promiseService);
  const services = new DeckRuntimeServices(router, promiseService, timeoutService, logger, providerServiceDelegate);

  return {
    router,
    promiseService,
    timeoutService,
    logger,
    interpolate,
    services,
    dispose: () => {
      services.dispose();
      timeoutService.dispose();
    },
  };
}
