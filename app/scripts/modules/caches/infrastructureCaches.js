'use strict';

/* jshint newcap: false */
angular.module('deckApp.caches.infrastructure', [
  'angular-data.DSCacheFactory',
])
  .factory('infrastructureCaches', function($cacheFactory, DSCacheFactory) {

    var caches = Object.create(null);

    function clearCaches() {
      var keys = Object.keys(caches);
      keys.forEach(function(key) {
        if (caches[key].destroy) {
          caches[key].destroy();
        }
      });
      createCaches();
    }

    caches.clearCaches = clearCaches;

    function addLocalStorageCache(cacheId, maxAge) {
      maxAge = maxAge || 2 * 24 * 60 * 60 * 1000;
      DSCacheFactory(cacheId, {
        maxAge: maxAge,
        deleteOnExpire: 'aggressive',
        storageMode: 'localStorage',
      });
      caches[cacheId] = DSCacheFactory.get(cacheId);
    }

    function createCaches() {
      addLocalStorageCache('credentials');
      addLocalStorageCache('vpcs');
      addLocalStorageCache('subnets');
      addLocalStorageCache('applications', 30 * 24 * 60 * 60 * 1000); // 30 days - it gets refreshed every time the user goes to the application list, anyway
      addLocalStorageCache('loadBalancers', 60 * 60 * 1000); // 60 minute cache on load balancers
      addLocalStorageCache('securityGroups');
      addLocalStorageCache('instanceTypes', 7 * 24 * 60 * 60 * 1000); // instance types are good for a week
    }

    createCaches();

    return caches;
  });
