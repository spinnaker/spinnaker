'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.instance', [
    require('./details/aws/instance.details.controller.js'),
    require('./details/gce/instance.details.controller.js'),
    require('./loadBalancer/instanceLoadBalancerHealth.js'),
  ]);
