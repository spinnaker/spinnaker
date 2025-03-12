import { UIRouter } from '@uirouter/core';
import 'kayenta/metricStore/index';
import 'kayenta/report/detail/graph/semiotic';

import { ApplicationStateProvider } from '@spinnaker/core';

// This import has a side effect of instantiating the canary redux store
import { bridgeKayentaDataSourceToReduxStore } from './kayenta/canary.dataSource.bridge';
import { registerStates, registerTransitionHooks } from './kayenta/navigation/canary.states';

export function lazyInitializeKayenta(applicationState: ApplicationStateProvider, uiRouter: UIRouter) {
  const { stateRegistry } = uiRouter;

  // deregister the stub states, starting with the deepest children first
  stateRegistry
    .get()
    .filter((state) => state.name.match(/home.(project|applications).application.canary/))
    .sort((a, b) => b.name.length - a.name.length)
    .forEach((stub) => stateRegistry.deregister(stub));

  // Now register the full states
  registerStates(uiRouter, applicationState);
  registerTransitionHooks(uiRouter);

  // Patch the data source so data flows into the redux store
  bridgeKayentaDataSourceToReduxStore();

  return {};
}
