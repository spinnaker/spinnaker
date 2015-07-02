'use strict';

require('../../../../fonts/spinnaker/icons.css');
let angular = require('angular');

module.exports = angular.module('spinnaker.search.global', [
  require('utils/jQuery.js'),
  require('utils/lodash.js'),
  require('./globalSearch.directive.js'),
  require('../../clusterFilter/clusterFilterModel.js'),
  require('../../clusterFilter/clusterFilterService.js'),
]).name;
