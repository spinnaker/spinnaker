'use strict';

const angular = require('angular');

import { InfrastructureCaches } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.configure.loadBalancers.controller', [
    require('../configuration.service').name,
  ])
  .controller('kubernetesServerGroupLoadBalancersController', function(
    kubernetesServerGroupConfigurationService,
    $scope,
  ) {
    this.refreshLoadBalancers = function() {
      kubernetesServerGroupConfigurationService.refreshLoadBalancers($scope.command, false);
    };

    this.getLoadBalancerRefreshTime = function() {
      return InfrastructureCaches.get('loadBalancers').getStats().ageMax;
    };
  });
