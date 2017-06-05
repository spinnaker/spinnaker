import { ICompileProvider, IHttpProvider, ILocationProvider, ILogProvider, IQProvider } from 'angular';

import { bootstrapModule } from './bootstrap.module';
import { SETTINGS } from 'core/config';

bootstrapModule
  .config(($logProvider: ILogProvider) => {
    'ngInject';
    $logProvider.debugEnabled(SETTINGS.debugEnabled);
  });

bootstrapModule.config(($httpProvider: IHttpProvider) => {
  'ngInject';
  $httpProvider.defaults.headers.patch = {
    'Content-Type': 'application/json;charset=utf-8'
  };
});

// Angular 1.6 stops suppressing unhandle rejections on promises. This resets it back to 1.5 behavior.
// See https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
bootstrapModule.config(($qProvider: IQProvider) => {
  'ngInject';
  $qProvider.errorOnUnhandledRejections(false);
});

// Angular 1.6 defaults preAssignBindingsEnabled to false, reset to true to mimic 1.5 behavior.
// See https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
bootstrapModule.config(($compileProvider: ICompileProvider) => {
  'ngInject';
  $compileProvider.aHrefSanitizationWhitelist(/^\s*(https?|mailto|hipchat|slack|ssh):/);
  $compileProvider.preAssignBindingsEnabled(true);
});

// Angular 1.6 sets default hashPrefix to '!', change it back to ''
// See https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$location
bootstrapModule.config(($locationProvider: ILocationProvider) => {
  'ngInject';
  $locationProvider.hashPrefix('');
});
