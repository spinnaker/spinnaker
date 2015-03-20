'use strict';

/* jshint newcap: false */
angular.module('deckApp.pipelines.services.viewStateCache', [
  'angular-data.DSCacheFactory',
]).factory('pipelineViewStateCache', function(DSCacheFactory) {

  var cacheId = 'pipelineViewStateCache';
  DSCacheFactory(cacheId, {
    maxAge: 30 * 24 * 60 * 60 * 1000, // 30 days
    deleteOnExpire: 'aggressive',
    storageMode: 'localStorage',
  });

  var stateCache = DSCacheFactory.get(cacheId);

  function buildCacheKey(application, pipelineName) {
    return [application, pipelineName].join('#');
  }

  return {
    hasCachedViewState: function(application, name) {
      return stateCache.get(buildCacheKey(application, name)) !== undefined;
    },
    getCachedViewState: function(application, name) {
      return stateCache.get(buildCacheKey(application, name));
    },
    cacheViewState: function(application, name, viewState) {
      return stateCache.put(buildCacheKey(application, name), viewState);
    },
    clearViewState: function(application, name) {
      stateCache.remove(buildCacheKey(application, name));
    }
  };
});
