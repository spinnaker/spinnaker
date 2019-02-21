// Random deck initialization code
import { IRootScopeService } from 'angular';

import { bootstrapModule } from './bootstrap.module';
import { SETTINGS } from 'core/config';
import { CacheInitializerService } from 'core/cache';
import { StateService } from '@uirouter/core';

bootstrapModule.run([
  '$rootScope',
  '$state',
  ($rootScope: IRootScopeService, $state: StateService) => {
    'ngInject';
    $rootScope.feature = SETTINGS.feature;
    $rootScope.$state = $state; // TODO: Do we really need this?
  },
]);

bootstrapModule.run([
  'cacheInitializer',
  (cacheInitializer: CacheInitializerService) => {
    'ngInject';
    cacheInitializer.initialize();
  },
]);
