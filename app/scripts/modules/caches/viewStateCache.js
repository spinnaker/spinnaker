'use strict';

/* jshint newcap: false */
angular.module('spinnaker.caches.viewStateCache', [
  'spinnaker.caches.core',

]).factory('viewStateCache', function(deckCacheFactory) {

  // TODO: Remove the next line any time after 5/1/15 - just a legacy bit to clear out old, pre-versioned LS
  deckCacheFactory.createCache(null, 'pipelineViewStateCache', {});


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
