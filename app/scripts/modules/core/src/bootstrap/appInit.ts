// Random deck initialization code
import { StateService } from '@uirouter/core';
import { IRootScopeService } from 'angular';
import { CacheInitializerService } from 'core/cache';
import { SETTINGS } from 'core/config';

import { bootstrapModule } from './bootstrap.module';

bootstrapModule.run([
  '$rootScope',
  '$state',
  ($rootScope: IRootScopeService, $state: StateService) => {
    $rootScope.feature = SETTINGS.feature;
    $rootScope.$state = $state; // TODO: Do we really need this?
  },
]);

bootstrapModule.run([
  'cacheInitializer',
  (cacheInitializer: CacheInitializerService) => {
    cacheInitializer.initialize();
  },
]);
