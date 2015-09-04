'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.instance', [
    require('./details/console/consoleOutput.modal.controller.js'),
    require('./loadBalancer/instanceLoadBalancerHealth.js'),
  ]).name;
