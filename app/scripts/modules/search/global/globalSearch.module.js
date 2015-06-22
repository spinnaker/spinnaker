'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search.global', [
  require('../../utils/jQuery.js'),
  require('../../utils/lodash.js'),
  require('../../clusterFilter/clusterFilterModel.js'),
  require('../../clusterFilter/clusterFilterService.js'),
]);
