// Random deck initialization code
import { IRootScopeService } from 'angular';

const Spinner = require('spin.js');
import { bootstrapModule } from './bootstrap.module';
import { SETTINGS } from 'core/config';
import { CacheInitializerService } from 'core/cache';
import { StateService } from '@uirouter/core';

bootstrapModule.run(($rootScope: IRootScopeService, $state: StateService) => {
  'ngInject';
  (window as any).Spinner = Spinner;

  $rootScope.feature = SETTINGS.feature;
  $rootScope.$state = $state; // TODO: Do we really need this?
});

bootstrapModule.run((cacheInitializer: CacheInitializerService) => {
  'ngInject';
  cacheInitializer.initialize();
});
