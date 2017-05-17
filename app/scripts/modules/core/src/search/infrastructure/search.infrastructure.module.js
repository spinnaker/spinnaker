'use strict';

const angular = require('angular');

import './infrastructure.less';

module.exports = angular.module('spinnaker.search.infrastructure', [
  require('./infrastructure.controller.js')
]);
