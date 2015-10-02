'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search', [
  require('../core/cache/deckCacheFactory.js'),
  require('./infrastructure/search.infrastructure.module.js'),
  require('./global/globalSearch.module.js'),
]).name;
