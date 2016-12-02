'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.search', [
  require('./infrastructure/search.infrastructure.module.js'),
  require('./global/globalSearch.module.js'),
]);
