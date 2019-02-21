'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.loadBalancer.configure.kubernetes.ports', [])
  .controller('kubernetesLoadBalancerPortsController', [
    '$scope',
    function($scope) {
      this.addPort = function() {
        $scope.loadBalancer.ports.push({});
      };

      this.removePort = function(index) {
        $scope.loadBalancer.ports.splice(index, 1);
      };

      this.protocols = ['TCP', 'UDP'];
      this.maxPort = 65535;
    },
  ]);
