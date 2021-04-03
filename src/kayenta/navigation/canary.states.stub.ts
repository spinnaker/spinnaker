import { StateDeclaration, UIRouter } from '@uirouter/core';

import { ApplicationStateProvider } from '@spinnaker/core';

export function registerKayentaStateStubs(applicationState: ApplicationStateProvider, uiRouter: UIRouter) {
  const states: StateDeclaration[] = [
    {
      name: 'canary',
      url: '/canary',
      lazyLoad: () =>
        import(/* webpackChunkName: "Lazy-Kayenta-Tabs" */ '../../lazy').then((m) =>
          m.lazyInitializeKayenta(applicationState, uiRouter),
        ),
    },
    { name: 'canary.canaryConfig', url: '/config' },
    { name: 'canary.canaryConfig.configDetail', url: '/:id?copy&new' },
    { name: 'canary.canaryConfig.configDefault', url: '' },
    { name: 'canary.report', url: '/report?count' },
    { name: 'canary.report.reportDetail', url: '/:configId/:runId' },
    { name: 'canary.report.reportDefault', url: '' },
  ];

  ['home.project', 'home.applications'].forEach((prefix) => {
    states
      .map((state) => ({ ...state, name: `${prefix}.application.${state.name}` } as StateDeclaration))
      .forEach((state) => uiRouter.stateRegistry.register(state as StateDeclaration));
  });
}
