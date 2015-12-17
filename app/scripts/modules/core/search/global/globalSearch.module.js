'use strict';

require('../../../../../fonts/spinnaker/icons.css');
require('./globalSearch.less');

let angular = require('angular');

module.exports = angular.module('spinnaker.core.search.global', [
  require('../../utils/jQuery.js'),
  require('../../utils/lodash.js'),
  require('./globalSearch.directive.js'),
  require('../../cluster/filter/clusterFilter.model.js'),
  require('../../cluster/filter/clusterFilter.service.js'),
]);
