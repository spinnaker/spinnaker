/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * License: MIT
 */
(function (angular) {
  'use strict';

  /**
   * @ngdoc overview
   * @name angulartics.splunk
   * Enables analytics support for with custom collection backend API
   * using (sp.js as described in http://blogs.splunk.com/2013/10/17/still-using-3rd-party-web-analytics-providers-build-your-own-using-splunk/)
   */
  angular.module('angulartics.splunk', ['angulartics'])
  .config(['$analyticsProvider', function ($analyticsProvider) {

    var errorFunction = function(){
      throw "Define sp ";
    };

    var _getSp = function () {
        return window.sp || { pageview: errorFunction, track: errorFunction };
    };

    $analyticsProvider.registerPageTrack(function (path) {
        _getSp().pageview(path);
    });

    $analyticsProvider.registerEventTrack(function (action, properties) {
        _getSp().track(action, properties);
    });

  }]);
})(angular);

