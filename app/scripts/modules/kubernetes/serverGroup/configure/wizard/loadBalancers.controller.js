'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.loadBalancers', [
  require('../configuration.service.js')
])
  .controller('kubernetesServerGroupLoadBalancersController', function(kubernetesServerGroupConfigurationService, infrastructureCaches, $scope) {
    this.refreshLoadBalancers = function() {
      kubernetesServerGroupConfigurationService.refreshLoadBalancers($scope.command, false);
    };

    this.getLoadBalancerRefreshTime = function() {
      return infrastructureCaches.get('loadBalancers').getStats().ageMax;
    };
  });
