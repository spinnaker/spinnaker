'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search', [
  require('../caches/deckCacheFactory.js'),
  require('../../services/urlbuilder.js'),
  require('./global/globalSearch.module.js'),
  require('./infrastructure/search.infrastructure.module.js'),
]);
