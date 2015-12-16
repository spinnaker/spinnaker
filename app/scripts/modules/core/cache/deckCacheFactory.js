'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cache.deckCacheFactory', [
  require('angular-cache'),
])
.factory('deckCacheFactory', function(CacheFactory, $log) {

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
        var info = cache.info(key) || {};
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

    var selfClearingLocalStorage = {
      setItem: function(k, v) {
        try {
          window.localStorage.setItem(k, v);
        } catch (e) {
          $log.warn('Local Storage Error! Clearing caches and trying again.\nException:', e);
          for (var key in window.localStorage) {
            // invalidate keystore for any angular caches
            if (key.match(/angular-cache\.caches\.infrastructure:.*.\.keys/)) {
              window.localStorage.setItem(key, '[]');
            }
            // clear the data itself
            if (key.match(/angular-cache\.caches\.infrastructure:.*.\.data\./)) {
              window.localStorage.removeItem(key);
            }
          }
          window.localStorage.setItem(k, v);
        }
      },
      getItem: function(k) { return window.localStorage.getItem(k); },
      removeItem: function(k) { return window.localStorage.removeItem(k); },
    };

    function addLocalStorageCache(namespace, cacheId, cacheConfig) {
      var key = buildCacheKey(namespace, cacheId);
      var cacheFactory = cacheConfig.cacheFactory || CacheFactory;
      var maxAge = cacheConfig.maxAge || 2 * 24 * 60 * 60 * 1000,
        currentVersion = cacheConfig.version || 1;

      clearPreviousVersions(namespace, cacheId, currentVersion, cacheFactory);

      cacheFactory.createCache(key, {
        maxAge: maxAge,
        deleteOnExpire: 'aggressive',
        storageMode: 'localStorage',
        storagePrefix: getStoragePrefix(key, currentVersion),
        recycleFreq: 5000, // ms,
        storageImpl: selfClearingLocalStorage,
        disabled: cacheConfig.disabled,
      });
      caches[key] = cacheFactory.get(key);
      caches[key].getStats = getStats.bind(null, caches[key]);
      caches[key].config = cacheConfig;
    }

    function clearPreviousVersions(namespace, cacheId, currentVersion, cacheFactory) {
      if (currentVersion) {

        // clear previous versions
        for (var i = 0; i < currentVersion; i++) {
          var key = buildCacheKey(namespace, cacheId);
          if (cacheFactory.get(key)) {
            cacheFactory.get(key).destroy();
          }
          cacheFactory.createCache(key, {
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
