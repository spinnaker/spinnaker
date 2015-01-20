/**
 * (c) 2015 Jared Dickson https://luisfarzati.github.io/angulartics
 * License: MIT
 */
(function (angular) {
    'use strict';

    /**
     * @ngdoc overview
     * @name angulartics.google.tagmanager.cordova
     * Enables analytics support for Google Tag Manager (http://google.com/tagmanager)
     * for Cordova plugin Tag Manager (http://plugins.cordova.io/#/package/com.jareddickson.cordova.tag-manager)
     */
    angular.module('angulartics.google.tagmanager.cordova', ['angulartics'])

        .provider('googleTagManagerCordova', function () {
            var GoogleTagManagerCordova = [
                '$q', '$log', 'ready', 'debug', 'trackingId', 'period',
                function ($q, $log, ready, debug, trackingId, period) {
                    var deferred = $q.defer();
                    var deviceReady = false;

                    document.addEventListener('deviceReady', function () {
                        deviceReady = true;
                        deferred.resolve();
                    }, false);

                    document.addEventListener('pause', function() {
                        var analytics = window.plugins && window.plugins.TagManager;
                        if (analytics) {
                            analytics.dispatch(success, failure);
                        }
                    }, false);

                    // iOS only
                    document.addEventListener('resign', function() {
                        var analytics = window.plugins && window.plugins.TagManager;
                        if (analytics) {
                            analytics.dispatch(success, failure);
                        }
                    }, false);

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
                            var analytics = window.plugins && window.plugins.TagManager;
                            if (analytics) {
                                analytics.init(function onInit() {
                                    ready(analytics, success, failure);
                                }, failure, trackingId, period || 30);
                            } else if (debug) {
                                $log.error('Google Tag Manager for Cordova is not available');
                            }
                        });
                    };
                }];

            return {
                $get: ['$injector', function ($injector) {
                    return $injector.instantiate(GoogleTagManagerCordova, {
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

        .config(['$analyticsProvider', 'googleTagManagerCordovaProvider', function ($analyticsProvider, googleTagManagerCordovaProvider) {
            googleTagManagerCordovaProvider.ready(function (analytics, success, failure) {
                $analyticsProvider.registerPageTrack(function (path) {
                    analytics.trackPage(success, failure, path);
                });

                $analyticsProvider.registerEventTrack(function (action, properties) {
                    analytics.trackEvent(success, failure, properties.category, action, properties.label, properties.value);
                });
            });
        }])

        .run(['googleTagManagerCordova', function (googleTagManagerCordova) {
            googleTagManagerCordova.init();
        }]);

})(angular);
