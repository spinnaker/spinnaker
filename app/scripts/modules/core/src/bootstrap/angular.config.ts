import { ICompileProvider, IHttpProvider, ILocationProvider, ILogProvider } from 'angular';

import { bootstrapModule } from './bootstrap.module';
import { SETTINGS } from '../config';

bootstrapModule.config([
  '$logProvider',
  ($logProvider: ILogProvider) => {
    $logProvider.debugEnabled(SETTINGS.debugEnabled);
  },
]);

bootstrapModule.config([
  '$httpProvider',
  ($httpProvider: IHttpProvider) => {
    $httpProvider.defaults.headers.patch = {
      'Content-Type': 'application/json;charset=utf-8',
    };
  },
]);

// Angular 1.6 defaults preAssignBindingsEnabled to false, reset to true to mimic 1.5 behavior.
// See https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
bootstrapModule.config([
  '$compileProvider',
  ($compileProvider: ICompileProvider) => {
    $compileProvider.aHrefSanitizationWhitelist(/^\s*(https?|mailto|slack|ssh):/);
    $compileProvider.preAssignBindingsEnabled(true);
  },
]);

// Angular 1.6 sets default hashPrefix to '!', change it back to ''
// See https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$location
bootstrapModule.config([
  '$locationProvider',
  ($locationProvider: ILocationProvider) => {
    $locationProvider.hashPrefix('');
    $locationProvider.html5Mode({ enabled: false, rewriteLinks: false });
  },
]);
