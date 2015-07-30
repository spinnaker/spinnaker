'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.providerSelection', [
  require('../caches/deckCacheFactory.js'),
]).name;
