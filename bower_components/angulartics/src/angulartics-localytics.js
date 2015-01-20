/**
 * @license Angulartics v0.17.2
 * (c) 2014 Luis Farzati http://luisfarzati.github.io/angulartics
 * Localytics plugin contributed by http://github.com/joehalliwell
 * License: MIT
 */
(function(angular) {
'use strict';

/**
 * @ngdoc overview
 * @name angulartics.localytics
 * Enables analytics support for Localytics (http://localytics.com/)
 */
angular.module('angulartics.localytics', ['angulartics'])
.config(['$analyticsProvider', function ($analyticsProvider) {

  $analyticsProvider.settings.trackRelativePath = true;

  /**
   * Register the page tracking function.
   */
  $analyticsProvider.registerPageTrack(function (path) {
    if (!window.localytics) return;
    window.localytics.tagScreen(path);
  });

  /**
   * Reigster the Localytics event tracking function with the following parameters:
   * @param {string} action Required 'action' (string) associated with the event
   * this becomes the event name
   * @param {object} properties Additional attributes to be associated with the
   * event. See http://support.localytics.com/Integration_Overview#Event_Attributes
   *
   */
  $analyticsProvider.registerEventTrack(function (action, properties) {
    if (!window.localytics) return;
    if (!action) return;
    window.localytics.tagEvent(action, properties);
  });

}]);
})(angular);
