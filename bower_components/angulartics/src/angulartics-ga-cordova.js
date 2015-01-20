/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * License: MIT
 */
(function(angular) {
'use strict';

/**
 * @ngdoc overview
 * @name angulartics.google.analytics
 * Enables analytics support for Google Analytics (http://google.com/analytics)
 */
angular.module('angulartics.google.analytics.cordova', ['angulartics'])

.provider('googleAnalyticsCordova', function () {
  var GoogleAnalyticsCordova = [
  '$q', '$log', 'ready', 'debug', 'trackingId', 'period',
  function ($q, $log, ready, debug, trackingId, period) {
    var deferred = $q.defer();
    var deviceReady = false;

    window.addEventListener('deviceReady', function () {
      deviceReady = true;
      deferred.resolve();
    });

    setTimeout(function () {
      if (!deviceReady) {
        deferred.resolve();
      }
    }, 3000);

    function success() {
      if (debug) {
        $log.info(arguments);
      }
    }

    function failure(err) {
      if (debug) {
        $log.error(err);
      }
    }

    this.init = function () {
      return deferred.promise.then(function () {
        var analytics = window.plugins && window.plugins.gaPlugin;
        if (analytics) {
          analytics.init(function onInit() {
            ready(analytics, success, failure);
          }, failure, trackingId, period || 10);
        } else if (debug) {
          $log.error('Google Analytics for Cordova is not available');
        }
      });
    };
  }];

  return {
    $get: ['$injector', function ($injector) {
      return $injector.instantiate(GoogleAnalyticsCordova, {
        ready: this._ready || angular.noop,
        debug: this.debug,
        trackingId: this.trackingId,
        period: this.period
      });
    }],
    ready: function (fn) {
      this._ready = fn;
    }
  };
})

.config(['$analyticsProvider', 'googleAnalyticsCordovaProvider', function ($analyticsProvider, googleAnalyticsCordovaProvider) {
  googleAnalyticsCordovaProvider.ready(function (analytics, success, failure) {
    $analyticsProvider.registerPageTrack(function (path) {
      analytics.trackPage(success, failure, path);
    });

    $analyticsProvider.registerEventTrack(function (action, properties) {
      analytics.trackEvent(success, failure, properties.category, action, properties.label, properties.value);
    });
  });
}])

.run(['googleAnalyticsCordova', function (googleAnalyticsCordova) {
  googleAnalyticsCordova.init();
}]);

})(angular);
