'use strict';

let angular = require('angular');

/* jshint newcap: false */
module.exports = angular.module('spinnaker.core.cache.infrastructure', [
  require('./deckCacheFactory.js')
])
  .factory('infrastructureCaches', function(deckCacheFactory) {

    var caches = Object.create(null);

    var namespace = 'infrastructure';

    function clearCache(key) {
      if (caches[key] && caches[key].removeAll) {
        caches[key].onReset.forEach((method) => method() );
        caches[key].removeAll();
      }
    }

    function clearCaches() {
      Object.keys(caches).forEach(clearCache);
    }

    function createCache(key, cacheConfig) {
      deckCacheFactory.createCache(namespace, key, cacheConfig);
      caches[key] = deckCacheFactory.getCache(namespace, key);
      caches[key].onReset = cacheConfig.onReset;
    }

    caches.clearCaches = clearCaches;
    caches.clearCache = clearCache;
    caches.createCache = createCache;

    return caches;
  });
