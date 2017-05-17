'use strict';

const angular = require('angular');

import './globalSearch.less';

module.exports = angular.module('spinnaker.core.search.global', [
  require('./globalSearch.directive.js'),
]);
