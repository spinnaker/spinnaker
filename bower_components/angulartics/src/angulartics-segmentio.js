/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * License: MIT
 */
(function(angular) {
  'use strict';

  /**
   * @ngdoc overview
   * @name angulartics.segment.io
   * Enables analytics support for Segment.io (http://segment.io)
   */
  angular.module('angulartics.segment.io', ['angulartics'])
    .config(['$analyticsProvider', function ($analyticsProvider) {

      // https://segment.com/docs/libraries/analytics.js/#page
      // analytics.page([category], [name], [properties], [options], [callback]);
      // TODO : Support optional parameters where the parameter order and type changes their meaning
      // e.g.
      // (string) is (name)
      // (string, string) is (category, name)
      // (string, object) is (name, properties)
      $analyticsProvider.registerPageTrack(function (path) {
        try {
          analytics.page(path);
        } catch (e) {
          if (!(e instanceof ReferenceError)) {
            throw e;
          }
        }
      });

      // https://segment.com/docs/libraries/analytics.js/#track
      // analytics.track(event, [properties], [options], [callback]);
      $analyticsProvider.registerEventTrack(function (event, properties, options, callback) {
        try {
          analytics.track(event, properties, options, callback);
        } catch (e) {
          if (!(e instanceof ReferenceError)) {
            throw e;
          }
        }
      });

      // Segment Identify Method
      // https://segment.com/docs/libraries/analytics.js/#identify
      // analytics.identify([userId], [traits], [options], [callback]);
      $analyticsProvider.registerSetUserProperties(function (userId, traits, options, callback) {
        try {
          analytics.identify(userId, traits, options, callback);
        } catch (e) {
          if (!(e instanceof ReferenceError)) {
            throw e;
          }
        }
      });

      // Segment Identify Method
      // https://segment.com/docs/libraries/analytics.js/#identify
      // analytics.identify([userId], [traits], [options], [callback]);
      $analyticsProvider.registerSetUserPropertiesOnce(function (userId, traits, options, callback) {
        try {
          analytics.identify(userId, traits, options, callback);
        } catch (e) {
          if (!(e instanceof ReferenceError)) {
            throw e;
          }
        }
      });

    }]);
})(angular);
