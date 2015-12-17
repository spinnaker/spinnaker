'use strict';

let angular = require('angular');

/* jshint newcap: false */
module.exports = angular.module('spinnaker.core.cache.collapsibleSectionState', [
  require('angular-cache'),
])
  .factory('collapsibleSectionStateCache', function(CacheFactory) {

    var cacheId = 'collapsibleSectionStateCache';

    try {
      CacheFactory.createCache(cacheId, {
        maxAge: 30 * 24 * 60 * 60 * 1000, // 30 days
        deleteOnExpire: 'aggressive',
        storageMode: 'localStorage',
      });
    } catch (e) {
      // trying to create a cache multiple times throws and Error
    }

    var stateCache = CacheFactory.get(cacheId);

    return {
      isSet: function(heading) {
        return stateCache.get(heading) !== undefined;
      },
      isExpanded: function(heading) {
        return stateCache.get(heading) === true;
      },
      setExpanded: function(heading, expanded) {
        stateCache.put(heading, !!expanded);
      }
    };
  });
