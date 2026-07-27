import type { UIRouter } from '@uirouter/core';

import type { ApplicationStateProvider } from '@spinnaker/core';

import { initializeCanaryStore } from './kayenta/canary';
import { bridgeKayentaDataSourceToReduxStore } from './kayenta/canary.dataSource.bridge';
import './kayenta/metricStore/index';
import { registerStates, registerTransitionHooks } from './kayenta/navigation/canary.states';
import './kayenta/report/detail/graph/semiotic';

interface ILazyKayentaDependencies {
  initializeStore: typeof initializeCanaryStore;
  registerStates: typeof registerStates;
  registerTransitionHooks: typeof registerTransitionHooks;
  bridgeDataSource: typeof bridgeKayentaDataSourceToReduxStore;
}

const lazyKayentaDependencies: ILazyKayentaDependencies = {
  initializeStore: initializeCanaryStore,
  registerStates,
  registerTransitionHooks,
  bridgeDataSource: bridgeKayentaDataSourceToReduxStore,
};

export function lazyInitializeKayenta(
  applicationState: ApplicationStateProvider,
  uiRouter: UIRouter,
  dependencies: ILazyKayentaDependencies = lazyKayentaDependencies,
) {
  const { stateRegistry } = uiRouter;
  dependencies.initializeStore(uiRouter);

  // deregister the stub states, starting with the deepest children first
  stateRegistry
    .get()
    .filter((state) => state.name.match(/home.(project|applications).application.canary/))
    .sort((a, b) => b.name.length - a.name.length)
    .forEach((stub) => stateRegistry.deregister(stub));

  // Now register the full states
  dependencies.registerStates(uiRouter, applicationState);
  dependencies.registerTransitionHooks(uiRouter);

  // Patch the data source so data flows into the redux store
  dependencies.bridgeDataSource();

  return {};
}
