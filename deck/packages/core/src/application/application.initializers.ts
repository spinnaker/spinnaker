import type { UIRouter } from '@uirouter/core';
import { module } from 'angular';

import type { ApplicationStateProvider } from './application.state.provider';

export type ApplicationInitializer = (applicationState: ApplicationStateProvider, uiRouter: UIRouter) => void;

const initializers: ApplicationInitializer[] = [];
let appliedContext: { applicationState: ApplicationStateProvider; uiRouter: UIRouter } | null = null;

export function registerApplicationInitializer(initializer: ApplicationInitializer): void {
  initializers.push(initializer);
  if (appliedContext) {
    initializer(appliedContext.applicationState, appliedContext.uiRouter);
  }
}

export function applyApplicationInitializers(applicationState: ApplicationStateProvider, uiRouter: UIRouter): void {
  if (appliedContext?.applicationState === applicationState && appliedContext?.uiRouter === uiRouter) {
    return;
  }

  appliedContext = { applicationState, uiRouter };
  initializers.forEach((initializer) => initializer(applicationState, uiRouter));
}

export function resetApplicationInitializersForTests(): void {
  initializers.length = 0;
  appliedContext = null;
}

export const APPLICATION_INITIALIZERS_MODULE = 'spinnaker.core.application.initializers';

module(APPLICATION_INITIALIZERS_MODULE, []).run([
  'applicationState',
  '$uiRouter',
  (applicationState: ApplicationStateProvider, uiRouter: UIRouter) => {
    applyApplicationInitializers(applicationState, uiRouter);
  },
]);
