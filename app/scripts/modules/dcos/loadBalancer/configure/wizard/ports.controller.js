'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.loadBalancer.configure.ports', [])
  .controller('dcosLoadBalancerPortsController', function() {

    this.protocols = ['tcp', 'udp'];
    this.minPort = 10000;
    this.maxPort = 65535;
  });
