'use strict';

let angular = require('angular');

require('./instanceSelection.less');

module.exports = angular
  .module('spinnaker.core.instance', [
    require('./details/console/consoleOutputLink.directive.js'),
    require('./loadBalancer/instanceLoadBalancerHealth.directive.js'),
  ]);
