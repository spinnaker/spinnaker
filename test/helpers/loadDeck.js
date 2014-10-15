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

    if (config.generateUrls) {
      var settings = $injector.get('settings');
      Object.keys(settings).forEach(function(key) {
        if (key.indexOf('Url') !== -1) {
          settings[key] = key;
        }
      });
      $provide.constant('settings', settings);
    }
  });
};

var loadDeckWithoutCacheInitializer = function() {
  return loadDeck({
    initializeCache: false,
    generateUrls: true,
  });
};
