'use strict';

const angular = require('angular');

import { NameUtils } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.kubernetes.deployment', [])
  .controller('kubernetesServerGroupDeploymentController', ['$scope', function($scope) {
    this.strategyTypes = ['RollingUpdate', 'Recreate'];

    this.deploymentConfigWarning = function() {
      var command = $scope.command;
      var name = NameUtils.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
      var clusters = $scope.application.clusters;
      if (!clusters) {
        return undefined;
      }

      var cluster = clusters.find(cluster => cluster.name === name && cluster.account === command.account);
      if (!cluster) {
        // In the case where there is no cluster, it doesn't matter if a
        // deployment is used or not since it's the first server group in the
        // cluster.
        return undefined;
      }

      var serverGroups = cluster.serverGroups.filter(serverGroup => serverGroup.region === command.namespace);
      if (!serverGroups) {
        // Again, this will be the first deployed server group that can decide
        // whether or not to depend on a deployment.
        return undefined;
      }

      var managedByDeployment = serverGroups.find(serverGroup => serverGroup.buildInfo.createdBy);

      var deploymentEnabled = command.deployment && command.deployment.enabled;
      if (managedByDeployment && !deploymentEnabled) {
        return (
          'The cluster ' +
          name +
          ' is already managed by a deployment. ' +
          "It's recommended that you enable it for all server groups in the cluster."
        );
      } else if (!managedByDeployment && deploymentEnabled) {
        return (
          'The cluster ' +
          name +
          ' is not already managed by a deployment. ' +
          "If you deploy this server group with a deployment enabled, old server groups won't be scaled down."
        );
      } else {
        return undefined;
      }
    };
  }]);
