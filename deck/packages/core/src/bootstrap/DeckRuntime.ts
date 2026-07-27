import type { UIRouterReact } from '@uirouter/react';

import { DeckRuntimeServices } from './DeckRuntimeServices';
import { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import { RoutingState } from '../navigation/RoutingState';
import type { CancellableTimeout } from '../utils/cancellableTimeout';
import { createCancellableTimeout } from '../utils/cancellableTimeout';
import type { DiagnosticLogger } from '../utils/diagnosticLogger';
import { createDiagnosticLogger } from '../utils/diagnosticLogger';
import { interpolate } from '../utils/interpolate';
import type { PromiseService } from '../utils/nativePromiseService';
import { createNativePromiseService } from '../utils/nativePromiseService';

export interface DeckRuntime {
  router: UIRouterReact | null;
  promiseService: PromiseService;
  timeoutService: CancellableTimeout;
  logger: DiagnosticLogger;
  interpolate: typeof interpolate;
  services: DeckRuntimeServices;
  routingState: RoutingState;
  dispose: () => void;
}

export function createDeckRuntime(router: UIRouterReact | null = null): DeckRuntime {
  const promiseService = createNativePromiseService();
  const timeoutService = createCancellableTimeout();
  const logger = createDiagnosticLogger();
  const providerServiceDelegate = new ProviderServiceDelegate(promiseService);
  const services = new DeckRuntimeServices(router, promiseService, timeoutService, logger, providerServiceDelegate);
  const routingState = new RoutingState();

  return {
    router,
    promiseService,
    timeoutService,
    logger,
    interpolate,
    services,
    routingState,
    dispose: () => {
      services.dispose();
      routingState.dispose();
      timeoutService.dispose();
    },
  };
}
