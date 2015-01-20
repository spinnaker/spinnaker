/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * License: MIT
 */
(function(angular) {
'use strict';

/**
 * @ngdoc overview
 * @name angulartics.woopra
 * Enables analytics support for Woopra (http://www.woopra.com)
 */
angular.module('angulartics.woopra', ['angulartics'])
.config(['$analyticsProvider', function ($analyticsProvider) {
  $analyticsProvider.registerPageTrack(function (path) {
    woopra.track('pv', {
      url: path
    });
  });

  $analyticsProvider.registerEventTrack(function (action, properties) {
    woopra.track(action, properties);
  });
}]);
})(angular);
