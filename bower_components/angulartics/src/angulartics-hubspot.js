(function (angular) {
  'use strict';

  /**
   * @ngdoc overview
   * @name angulartics.hubspot
   * Enables analytics support for Hubspot (http://www.hubspot.com)
   */
  angular.module('angulartics.hubspot', ['angulartics'])
    .config(['$analyticsProvider', function ($analyticsProvider) {
      // Don't send the first page, hubspot does it
      $analyticsProvider.settings.pageTracking.autoTrackFirstPage = false;

      /**
       * Track a page view
       */
      $analyticsProvider.registerPageTrack(function (path) {
        if (window._hsq) {
          _hsq.push();
          _hsq.push(['trackPageView', path]);
        }
      });

      // https://developers.hubspot.com/docs/methods/enterprise_events/javascript_api
      $analyticsProvider.registerEventTrack(function (action, properties) {
        if(properties.value) {
          var parsed = parseInt(properties.value, 10);
          properties.value = isNaN(parsed) ? 0 : parsed;
        }

        if (window._hsq) {
          _hsq.push();
          _hsq.push(["trackEvent", action, {value: properties.value}]);

        }
      });
    }]);
})(angular);
