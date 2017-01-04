'use strict';

require('../../../../../fonts/spinnaker/icons.css');
require('./globalSearch.less');

let angular = require('angular');

module.exports = angular.module('spinnaker.core.search.global', [
  require('../../utils/jQuery.js'),
  require('./globalSearch.directive.js'),
]);
