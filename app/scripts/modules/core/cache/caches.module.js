'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.cache', [
    require('./deckCacheFactory.js'),
    require('./cacheInitializer.js'),
    require('./collapsibleSectionStateCache.js'),
    require('./infrastructureCaches.js'),
  ])
  .run(function(cacheInitializer) {
    cacheInitializer.initialize();
  });
