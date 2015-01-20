/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * Contributed by http://github.com/samanbarghi
 * License: MIT
 */

(function(angular) {
'use strict';

/**
 * @ngdoc overview
 * @name angulartics.flurry
 * Enables analytics support for flurry (http://flurry.com)
 */
angular.module('angulartics.flurry', ['angulartics'])
.config(['$analyticsProvider', function ($analyticsProvider) {


  $analyticsProvider.registerPageTrack(function (path) {
    //No separate track page functionality
  });

  $analyticsProvider.registerEventTrack(function (action, properties) {
    FlurryAgent.logEvent(action, properties);
  });

}]);
})(angular);
