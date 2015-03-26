'use strict';

/* jshint newcap: false */
angular.module('deckApp.caches.infrastructure', [
  'angular-data.DSCacheFactory',
])
  .factory('infrastructureCaches', function(DSCacheFactory) {

    var caches = Object.create(null);

    function clearCache(key) {
      if (caches[key] && caches[key].destroy) {
        caches[key].destroy();
        createCache(key);
      }
    }

    function clearCaches() {
      Object.keys(caches).forEach(clearCache);
    }

    function createCache(key, config) {
      addLocalStorageCache(key, config);
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

    function getStoragePrefix(cacheId, version) {
      return 'angular-cache.caches.' + cacheId + ':' + version + '.';
    }

    function addLocalStorageCache(cacheId, cacheConfig) {
      var cacheFactory = cacheConfig.cacheFactory || DSCacheFactory;
      var maxAge = cacheConfig.maxAge || 2 * 24 * 60 * 60 * 1000,
          currentVersion = cacheConfig.version || 1;

      clearPreviousVersions(cacheId, currentVersion, cacheFactory);

      cacheFactory(cacheId, {
        maxAge: maxAge,
        deleteOnExpire: 'aggressive',
        storageMode: 'localStorage',
        storagePrefix: getStoragePrefix(cacheId, currentVersion),
        recycleFreq: 5000, // ms
      });
      caches[cacheId] = cacheFactory.get(cacheId);
      caches[cacheId].getStats = getStats.bind(null, caches[cacheId]);
    }

    function clearPreviousVersions(cacheId, currentVersion, cacheFactory) {
      if (currentVersion) {

        // clear non-versioned cache
        cacheFactory(cacheId, { storageMode: 'localStorage', });
        cacheFactory.get(cacheId).removeAll();
        cacheFactory.get(cacheId).destroy();

        // clear previous versions
        for (var i = 1; i < currentVersion; i++) {
          cacheFactory(cacheId, {
            storageMode: 'localStorage',
            storagePrefix: getStoragePrefix(cacheId, i),
          });
          cacheFactory.get(cacheId).removeAll();
          cacheFactory.get(cacheId).destroy();
        }
      }

    }

    caches.clearCaches = clearCaches;
    caches.clearCache = clearCache;
    caches.createCache = createCache;
    caches.getStoragePrefix = getStoragePrefix;

    return caches;
  });
