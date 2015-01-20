/**
 * @license Angulartics v0.17.2
 * (c) 2013 Luis Farzati http://luisfarzati.github.io/angulartics
 * Piwik 2.1.x update contributed by http://github.com/highskillz
 * License: MIT
 */
(function(angular) {
    'use strict';

    /**
     * @ngdoc overview
     * @name angulartics.piwik
     * Enables analytics support for Piwik (http://piwik.org/docs/tracking-api/)
     */
    angular.module('angulartics.piwik', ['angulartics'])
        .config(['$analyticsProvider',
            function($analyticsProvider) {

                // Piwik seems to suppors buffered invocations so we don't need
                // to wrap these inside angulartics.waitForVendorApi

                $analyticsProvider.settings.trackRelativePath = true;

                $analyticsProvider.registerPageTrack(function(path) {
                    if (window._paq) {
                        _paq.push(['setCustomUrl', path]);
                        _paq.push(['trackPageView']);
                    }
                });

                $analyticsProvider.registerEventTrack(function(action, properties) {
                    // PAQ requires that eventValue be an integer, see:
                    // http://piwik.org/docs/event-tracking/
                    if(properties.value) {
                        var parsed = parseInt(properties.value, 10);
                        properties.value = isNaN(parsed) ? 0 : parsed;
                    }

                    if (window._paq) {
                        _paq.push(['trackEvent', properties.category, action, properties.label, properties.value]);
                    }
                });

            }
        ]);
})(angular);
