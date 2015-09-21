'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.caches', [
    require('./deckCacheFactory.js'),
    require('./cacheInitializer.js'),
    require('./collapsibleSectionStateCache.js'),
    require('./infrastructureCaches.js'),
  ])
  .name;
