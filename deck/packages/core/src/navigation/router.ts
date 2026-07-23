import { hashLocationPlugin, servicesPlugin, UIRouterReact } from '@uirouter/react';
import { UIRouterRxPlugin } from '@uirouter/rx';

import { applyApplicationInitializers } from '../application/application.initializers';
import { getActiveApplicationStateProvider } from '../application/applicationState.registration';
import { setDirectRouter } from './directRouter';
import { registerRouteErrorBoundary } from '../presentation/SpinErrorBoundary';
import { applyRootStateRegistrations } from './rootState.registration';
import { registerRouteLifecycles } from './routeLifecycles';
import {
  booleanParamType,
  inverseBooleanParamType,
  sortKeyParamType,
  StateConfigProvider,
  trueKeyObjectParamType,
} from './state.provider';
import { StateHelper } from './stateHelper.provider';

export function configureRouter(): UIRouterReact {
  const router = new UIRouterReact();
  try {
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    router.plugin(UIRouterRxPlugin);
    const deregisterErrorBoundary = registerRouteErrorBoundary(router);
    router.disposable({ dispose: deregisterErrorBoundary });

    router.urlService.config.type('trueKeyObject', trueKeyObjectParamType);
    router.urlService.config.type('inverse-boolean', inverseBooleanParamType);
    router.urlService.config.type('boolean', booleanParamType);
    router.urlService.config.type('sortKey', sortKeyParamType);
    router.urlRouter.otherwise('/');
    router.urlRouter.when('/{path:.*}/', (match: any) => '/' + match.path);

    const stateConfig = new StateConfigProvider(router.urlRouter, new StateHelper(router.stateRegistry));
    applyRootStateRegistrations(stateConfig);
    const applicationStateProvider = getActiveApplicationStateProvider();
    stateConfig.setStates();
    if (applicationStateProvider) {
      applyApplicationInitializers(applicationStateProvider, (router as unknown) as any);
    }
    const deregisterRouteLifecycles = registerRouteLifecycles(router);
    router.disposable({ dispose: deregisterRouteLifecycles });
    setDirectRouter(router);
    return router;
  } catch (error) {
    router.dispose();
    throw error;
  }
}

export function startRouter(router: UIRouterReact): void {
  if (!router.started) {
    router.start();
  }
}
