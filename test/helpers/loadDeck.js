/* jshint unused: false */
/* global loadDeckWithoutCacheInitializer: true */

'use strict';

var loadDeck = function(config) {
  config = config || {};
  return module('deckApp', function($provide, $injector) {
    if (!config.initializeCache) {
      $provide.decorator('cacheInitializer', function() {
        return {
          initialize: angular.noop,
        };
      });
    }

    if (config.generateUrls || config.enableAuth) {
      var settings = $injector.get('settings');
      if (config.generateUrls) {
        Object.keys(settings).forEach(function (key) {
          if (key.indexOf('Url') !== -1) {
            settings[key] = key;
          }
        });
      }
      if (config.enableAuth) {
        settings.authEnabled = true;
      }
      $provide.constant('settings', settings);
    }

    if (config.authenticatedUser) {
      $provide.decorator('authenticationService', function() {
        return {
          authenticateUser: angular.noop,
          getAuthenticatedUser: function() {
            return { name: config.authenticatedUser, authenticated: true };
          }
        };
      });
    }
  });
};

var loadDeckWithoutCacheInitializer = function() {
  return loadDeck({
    initializeCache: false,
    generateUrls: true,
    authenticatedUser: 'kato@example.com',
    enableAuth: false,
  });
};
