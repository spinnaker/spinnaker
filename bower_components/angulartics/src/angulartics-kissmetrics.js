/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * License: MIT
 */
(function(angular) {
'use strict';

/**
 * @ngdoc overview
 * @name angulartics.kissmetrics
 * Enables analytics support for KISSmetrics (http://kissmetrics.com)
 */
angular.module('angulartics.kissmetrics', ['angulartics'])
.config(['$analyticsProvider', function ($analyticsProvider) {

  // KM already supports buffered invocations so we don't need
  // to wrap these inside angulartics.waitForVendorApi

  // Creates the _kqm array if it doesn't exist already
  // Useful if you want to load angulartics before kissmetrics

  if (typeof(_kmq) == "undefined") {
    window._kmq = [];
  } else {
    window._kmq = _kmq;
  }

  $analyticsProvider.registerPageTrack(function (path) {
    window._kmq.push(['record', 'Pageview', { 'Page': path }]);
  });

  $analyticsProvider.registerEventTrack(function (action, properties) {
    window._kmq.push(['record', action, properties]);
  });

  $analyticsProvider.registerSetUsername(function (uuid) {
    window._kmq.push(['identify', uuid]);
  });

  $analyticsProvider.registerSetUserProperties(function (properties) {
    window._kmq.push(['set', properties]);
  });

}]);
})(angular);
