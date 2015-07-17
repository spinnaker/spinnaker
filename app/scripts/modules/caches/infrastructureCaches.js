'use strict';

/* jshint newcap: false */
angular.module('spinnaker.caches.infrastructure', [
  'spinnaker.caches.core',
  'spinnaker.authentication.service',
  'spinnaker.settings',
])
  .factory('infrastructureCaches', function(deckCacheFactory, authenticationService, settings) {

    var caches = Object.create(null);

    var namespace = 'infrastructure';

    function clearCache(key) {
      if (caches[key] && caches[key].removeAll) {
        caches[key].removeAll();
      }
    }

    function clearCaches() {
      Object.keys(caches).forEach(clearCache);
    }

    function createCache(key, cacheConfig) {
      var shouldDisable = false;
      if (settings.authEnabled && cacheConfig.authEnabled && !authenticationService.getAuthenticatedUser().authenticated) {
        shouldDisable = true;
      }
      cacheConfig.disabled = shouldDisable;
      deckCacheFactory.createCache(namespace, key, cacheConfig);
      var cache = deckCacheFactory.getCache(namespace, key);
      if (shouldDisable) {
        authenticationService.onAuthentication(function() {
          cache.enable();
        });
      }
      caches[key] = cache;
    }

    caches.clearCaches = clearCaches;
    caches.clearCache = clearCache;
    caches.createCache = createCache;

    return caches;
  });
