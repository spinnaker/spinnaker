/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * Contributed by http://github.com/zoellner
 * License: MIT
 */
(function (angular) {
  'use strict';

  /**
   * @ngdoc overview
   * @name angulartics.loggly
   * Enables analytics support for Loggly
   */
  angular.module('angulartics.loggly', ['angulartics'])
  .config(['$analyticsProvider', function ($analyticsProvider) {

    var errorFunction = function(){
      throw "Define _LTracker ";
    };

    var _getLTracker = function () {
      return window._LTracker || { push: errorFunction};
    };

    $analyticsProvider.registerPageTrack(function (path) {
      _getLTracker().push({
        "tag": "pageview",
        "path": path
      });
    });

    $analyticsProvider.registerEventTrack(function (action, properties) {
      _getLTracker().push({
        "action": action,
        "properties": properties
      });
    });

  }]);
})(angular);
