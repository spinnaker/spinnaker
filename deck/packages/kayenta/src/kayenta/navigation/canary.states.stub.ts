import type { StateDeclaration, UIRouter } from '@uirouter/core';

import type { ApplicationStateProvider } from '@spinnaker/core';

interface ILazyKayentaModule {
  lazyInitializeKayenta: (applicationState: ApplicationStateProvider, uiRouter: UIRouter) => unknown;
}

type LoadLazyKayenta = () => Promise<ILazyKayentaModule>;

export function createKayentaStateStubs(
  applicationState: ApplicationStateProvider,
  uiRouter: UIRouter,
  loadLazyKayenta: LoadLazyKayenta = () => import(/* webpackChunkName: "Lazy-Kayenta-Tabs" */ '../../lazy'),
): StateDeclaration[] {
  return [
    {
      name: 'canary',
      url: '/canary',
      lazyLoad: () => loadLazyKayenta().then((module) => module.lazyInitializeKayenta(applicationState, uiRouter)),
    },
    { name: 'canary.canaryConfig', url: '/config' },
    { name: 'canary.canaryConfig.configDetail', url: '/:id?copy&new' },
    { name: 'canary.canaryConfig.configDefault', url: '' },
    { name: 'canary.report', url: '/report?count' },
    { name: 'canary.report.reportDetail', url: '/:configId/:runId' },
    { name: 'canary.report.reportDefault', url: '' },
  ];
}

export function registerKayentaStateStubs(applicationState: ApplicationStateProvider, uiRouter: UIRouter) {
  const states = createKayentaStateStubs(applicationState, uiRouter);

  ['home.project', 'home.applications'].forEach((prefix) => {
    states
      .map((state) => ({ ...state, name: `${prefix}.application.${state.name}` } as StateDeclaration))
      .forEach((state) => uiRouter.stateRegistry.register(state as StateDeclaration));
  });
}
