(function(angular) {
  'use strict';

  /**
   * @ngdoc overview
   * @name angulartics.cnzz
   * Enables analytics support for CNZZ (http://www.cnzz.com)
   */
  angular.module('angulartics.cnzz', ['angulartics'])
    .config(['$analyticsProvider', function ($analyticsProvider) {
      window._czc = _czc || [];
      _czc.push(['_setAutoPageview', false]);

      $analyticsProvider.registerPageTrack(function (path) {
        _czc.push(['_trackPageview', path]);
      });

      $analyticsProvider.registerEventTrack(function (action, prop) {
        _czc.push([
          '_trackEvent',
          prop.category,
          action,
          prop.label,
          prop.value,
          prop.nodeid
        ]);
      });
    }]);
})(angular);
