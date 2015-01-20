/**
 * @license Angulartics v0.17.2
 * (c) 2014 Luis Farzati http://luisfarzati.github.io/angulartics
 * License: MIT
 */
(function(angular) {
'use strict';

/**
 * @ngdoc overview
 * @name angulartics.debug
 * Enables analytics debugging to console
 */
angular.module('angulartics.debug', ['angulartics'])
.config(['$analyticsProvider', function ($analyticsProvider) {

  $analyticsProvider.registerPageTrack(function (path) {
    console.log('Page tracking: ', path);
  });

  /**
   * Track Event
   * @name eventTrack
   */
  $analyticsProvider.registerEventTrack(function (action, properties) {
    console.log("Event tracking: ", action, properties);
  });

}]);
})(angular);
