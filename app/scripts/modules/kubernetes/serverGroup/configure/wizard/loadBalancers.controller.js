'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.serverGroup.configure.loadBalancers.controller', [
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
