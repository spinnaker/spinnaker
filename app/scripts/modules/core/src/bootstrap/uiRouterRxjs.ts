import { UIRouter } from '@uirouter/core';
import { UIRouterRxPlugin } from '@uirouter/rx';

import { bootstrapModule } from './bootstrap.module';

/** Register the @uirouter/rx plugin to add observables for state changes, i.e., `router.globals.start$` */
bootstrapModule.config([
  '$uiRouterProvider',
  ($uiRouterProvider: UIRouter) => {
    $uiRouterProvider.plugin(UIRouterRxPlugin);
  },
]);
