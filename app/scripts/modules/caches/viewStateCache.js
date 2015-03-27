'use strict';

/* jshint newcap: false */
angular.module('deckApp.caches.viewStateCache', [
  'deckApp.caches.infrastructure',

]).factory('viewStateCache', function(infrastructureCaches) {

  // TODO: Remove the next line any time after 5/1/15 - just a legacy bit to clear out old, pre-versioned LS
  infrastructureCaches.createCache('pipelineViewStateCache', {});


  var cacheId = 'viewStateCache';
  infrastructureCaches.createCache(cacheId, {
    maxAge: 14 * 24 * 60 * 60 * 1000, // 14 days
    version: 1,
  });

  var stateCache = infrastructureCaches[cacheId];

  function buildCacheKey(application, viewStateKey) {
    return [application, viewStateKey].join('#');
  }

  return {
    hasCachedViewState: function(application, viewStateKey) {
      return stateCache.get(buildCacheKey(application, viewStateKey)) !== undefined;
    },
    getCachedViewState: function(application, viewStateKey) {
      return stateCache.get(buildCacheKey(application, viewStateKey));
    },
    cacheViewState: function(application, viewStateKey, viewState) {
      return stateCache.put(buildCacheKey(application, viewStateKey), viewState);
    },
    clearViewState: function(application, viewStateKey) {
      stateCache.remove(buildCacheKey(application, viewStateKey));
    }
  };
});
