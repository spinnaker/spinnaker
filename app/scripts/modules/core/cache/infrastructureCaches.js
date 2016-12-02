'use strict';

import {DECK_CACHE_FACTORY} from 'core/cache/deckCacheFactory';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cache.infrastructure', [DECK_CACHE_FACTORY])
  .factory('infrastructureCaches', function(deckCacheFactory) {

    var caches = Object.create(null);

    var namespace = 'infrastructure';

    function clearCache(key) {
      let cache = caches[key];
      if (cache && cache.removeAll) {
        cache.keys().forEach((k) => cache.remove(k));
        cache.onReset.forEach((method) => method() );
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
