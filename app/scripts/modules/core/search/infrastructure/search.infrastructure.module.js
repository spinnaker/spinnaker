'use strict';

let angular = require('angular');

require('./infrastructure.less');

module.exports = angular.module('spinnaker.search.infrastructure', [
  require('./infrastructure.controller.js')
]);
