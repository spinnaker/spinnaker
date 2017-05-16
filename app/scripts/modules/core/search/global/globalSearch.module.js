'use strict';

require('./globalSearch.less');

let angular = require('angular');

module.exports = angular.module('spinnaker.core.search.global', [
  require('./globalSearch.directive.js'),
]);
