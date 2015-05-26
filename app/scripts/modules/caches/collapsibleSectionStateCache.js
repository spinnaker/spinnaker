'use strict';

/* jshint newcap: false */
angular.module('spinnaker.caches.collapsibleSectionState', [
  'angular-data.DSCacheFactory',
])
  .factory('collapsibleSectionStateCache', function(DSCacheFactory) {

    var cacheId = 'collapsibleSectionStateCache';
    DSCacheFactory(cacheId, {
      maxAge: 30 * 24 * 60 * 60 * 1000, // 30 days
      deleteOnExpire: 'aggressive',
      storageMode: 'localStorage',
    });

    var stateCache = DSCacheFactory.get(cacheId);

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
