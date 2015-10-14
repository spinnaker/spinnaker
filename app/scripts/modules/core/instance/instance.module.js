'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.instance', [
    require('./details/console/consoleOutput.modal.controller.js'),
    require('./loadBalancer/instanceLoadBalancerHealth.directive.js'),
  ]).name;
