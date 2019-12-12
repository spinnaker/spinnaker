'use strict';

import { module } from 'angular';

import { NameUtils } from '@spinnaker/core';

export const KUBERNETES_V1_SERVERGROUP_CONFIGURE_WIZARD_DEPLOYMENT_CONTROLLER =
  'spinnaker.serverGroup.configure.kubernetes.deployment';
export const name = KUBERNETES_V1_SERVERGROUP_CONFIGURE_WIZARD_DEPLOYMENT_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_SERVERGROUP_CONFIGURE_WIZARD_DEPLOYMENT_CONTROLLER, []).controller(
  'kubernetesServerGroupDeploymentController',
  [
    '$scope',
    function($scope) {
      this.strategyTypes = ['RollingUpdate', 'Recreate'];

      this.deploymentConfigWarning = function() {
        const command = $scope.command;
        const name = NameUtils.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
        const clusters = $scope.application.clusters;
        if (!clusters) {
          return undefined;
        }

        const cluster = clusters.find(cluster => cluster.name === name && cluster.account === command.account);
        if (!cluster) {
          // In the case where there is no cluster, it doesn't matter if a
          // deployment is used or not since it's the first server group in the
          // cluster.
          return undefined;
        }

        const serverGroups = cluster.serverGroups.filter(serverGroup => serverGroup.region === command.namespace);
        if (!serverGroups) {
          // Again, this will be the first deployed server group that can decide
          // whether or not to depend on a deployment.
          return undefined;
        }

        const managedByDeployment = serverGroups.find(serverGroup => serverGroup.buildInfo.createdBy);

        const deploymentEnabled = command.deployment && command.deployment.enabled;
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
    },
  ],
);
