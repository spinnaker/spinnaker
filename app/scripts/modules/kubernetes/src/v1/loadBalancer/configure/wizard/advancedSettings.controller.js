'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.loadBalancer.configure.kubernetes.advancedSettings', [])
  .controller('kubernetesLoadBalancerAdvancedSettingsController', [
    '$scope',
    function($scope) {
      this.addExternalIp = function() {
        $scope.loadBalancer.externalIps.push({});
      };

      this.removeExternalIp = function(index) {
        $scope.loadBalancer.externalIps.splice(index, 1);
      };

      this.sessionAffinities = ['None', 'ClientIP'];
      this.types = ['ClusterIP', 'LoadBalancer', 'NodePort'];
    },
  ]);
