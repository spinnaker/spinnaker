'use strict';

/* jshint newcap: false */
angular.module('deckApp.caches.infrastructure', [
  'angular-data.DSCacheFactory',
])
  .factory('infrastructureCaches', function($cacheFactory, DSCacheFactory) {

    var caches = Object.create(null);

    var cacheConfig = {
      credentials: {},
      vpcs: {},
      subnets: {},
      applications: {
        maxAge: 30 * 24 * 60 * 60 * 1000 // 30 days - it gets refreshed every time the user goes to the application list, anyway
      },
      loadBalancers: {
        maxAge: 60 * 60 * 1000
      },
      securityGroups: {},
      instanceTypes: 7 * 24 * 60 * 60 * 1000,
      keyPairs: {},
    };

    function clearCache(key) {
      if (caches[key] && caches[key].destroy) {
        caches[key].destroy();
        createCache(key);
      }
    }

    function clearCaches() {
      Object.keys(caches).forEach(clearCache);
    }

    function createCache(key) {
      addLocalStorageCache(key, cacheConfig[key].maxAge);
    }

    function createCaches() {
      Object.keys(cacheConfig).forEach(createCache);
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

    function addLocalStorageCache(cacheId, maxAge) {
      maxAge = maxAge || 2 * 24 * 60 * 60 * 1000;
      DSCacheFactory(cacheId, {
        maxAge: maxAge,
        deleteOnExpire: 'aggressive',
        storageMode: 'localStorage',
      });
      caches[cacheId] = DSCacheFactory.get(cacheId);
      caches[cacheId].getStats = getStats.bind(null, caches[cacheId]);
    }

    caches.clearCaches = clearCaches;
    caches.clearCache = clearCache;

    createCaches();

    return caches;
  });
