'use strict';

import {DECK_CACHE_FACTORY} from 'core/cache/deckCacheFactory';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cache.viewStateCache', [DECK_CACHE_FACTORY])
  .factory('viewStateCache', function(deckCacheFactory) {

  var caches = Object.create(null);

  var namespace = 'viewStateCache';

  function clearCache(key) {
    if (caches[key] && caches[key].destroy) {
      caches[key].destroy();
      createCache(key, caches[key].config);
    }
  }

  function clearCaches() {
    Object.keys(caches).forEach(clearCache);
  }

  function createCache(key, config) {
    deckCacheFactory.createCache(namespace, key, config);
    caches[key] = deckCacheFactory.getCache(namespace, key);
    return caches[key];
  }

  caches.clearCaches = clearCaches;
  caches.clearCache = clearCache;
  caches.createCache = createCache;

  return caches;
});
