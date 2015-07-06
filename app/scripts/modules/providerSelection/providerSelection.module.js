'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.providerSelection', [
  require('angular-bootstrap'),
  require('../caches/deckCacheFactory.js'),
]).name;
