import { bootstrapModule } from './bootstrap.module';
import { UIRouterRxPlugin } from '@uirouter/rx';
import { UIRouter } from '@uirouter/core';

/** Register the @uirouter/rx plugin to add observables for state changes, i.e., `router.globals.start$` */
bootstrapModule.config([
  '$uiRouterProvider',
  ($uiRouterProvider: UIRouter) => {
    'ngInject';
    $uiRouterProvider.plugin(UIRouterRxPlugin);
  },
]);
