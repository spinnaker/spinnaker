'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.loadBalancer.configure.resources', [])
  .controller('dcosLoadBalancerResourcesController', function() {
    this.minCpus = 0.01;
  });
