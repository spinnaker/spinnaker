'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.caches.core', [
  require('angular-cache'),
])
.factory('deckCacheFactory', function(CacheFactory) {

    var caches = Object.create(null);

    function buildCacheKey(namespace, cacheId) {
      if (!namespace || !cacheId) {
        return namespace || cacheId;
      }
      return [namespace, cacheId].join(':');
    }

    function clearCache(namespace, key) {
      if (caches[key] && caches[key].destroy) {
        caches[key].destroy();
        createCache(namespace, key, caches[key].config);
      }
    }

    function clearCaches() {
      Object.keys(caches).forEach(clearCache);
    }

    function createCache(namespace, key, config) {
      addLocalStorageCache(namespace, key, config);
    }

    // Provides number of keys and min/max age of all keys in the cache
    function getStats(cache) {
      var keys = cache.keys(),
        ageMin = new Date().getTime(),
        ageMax = 0;

      keys.forEach(function (key) {
        var info = cache.info(key);
        ageMin = Math.min(ageMin, info.created);
        ageMax = Math.max(ageMax, info.created);
      });

      return {
        keys: keys.length,
        ageMin: ageMin || null,
        ageMax: ageMax || null
      };
    }

    function getStoragePrefix(key, version) {
      return 'angular-cache.caches.' + key + ':' + version + '.';
    }

    function addLocalStorageCache(namespace, cacheId, cacheConfig) {
      var key = buildCacheKey(namespace, cacheId);
      var cacheFactory = cacheConfig.cacheFactory || CacheFactory;
      var maxAge = cacheConfig.maxAge || 2 * 24 * 60 * 60 * 1000,
        currentVersion = cacheConfig.version || 1;

      clearPreviousVersions(namespace, cacheId, currentVersion, cacheFactory);

      cacheFactory.create(key, {
        maxAge: maxAge,
        deleteOnExpire: 'aggressive',
        storageMode: 'localStorage',
        storagePrefix: getStoragePrefix(key, currentVersion),
        recycleFreq: 5000, // ms
      });
      caches[key] = cacheFactory.get(key);
      caches[key].getStats = getStats.bind(null, caches[key]);
      caches[key].config = cacheConfig;
    }

    function clearPreviousVersions(namespace, cacheId, currentVersion, cacheFactory) {
      if (currentVersion) {

        // clear non-versioned cache (TODO: remove after 5/15/15)
        cacheFactory.create(cacheId, { storageMode: 'localStorage', });
        cacheFactory.get(cacheId).removeAll();
        cacheFactory.get(cacheId).destroy();

        // clear previous versions
        for (var i = 0; i < currentVersion; i++) {
          // non-namespaced (TODO: remove after 5/15/15)
          cacheFactory.create(cacheId, {
            storageMode: 'localStorage',
            storagePrefix: getStoragePrefix(cacheId, i+1),
          });
          cacheFactory.get(cacheId).removeAll();
          cacheFactory.get(cacheId).destroy();

          // namespaced
          var key = buildCacheKey(namespace, cacheId);
          if (cacheFactory.get(key)) {
            cacheFactory.get(key).destroy();
          }
          cacheFactory.create(key, {
            storageMode: 'localStorage',
            storagePrefix: getStoragePrefix(key, i),
          });
          cacheFactory.get(key).removeAll();
          cacheFactory.get(key).destroy();
        }
      }

    }

    function getCache(namespace, cacheId) {
      return caches[buildCacheKey(namespace, cacheId)];
    }

    caches.getCache = getCache;
    caches.clearCaches = clearCaches;
    caches.clearCache = clearCache;
    caches.createCache = createCache;
    caches.getStoragePrefix = getStoragePrefix;


    return caches;

  });
