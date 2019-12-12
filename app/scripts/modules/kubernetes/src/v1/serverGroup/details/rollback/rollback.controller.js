'use strict';

import { module } from 'angular';

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

export const KUBERNETES_V1_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACK_CONTROLLER =
  'spinnaker.kubernetes.serverGroup.details.rollback.controller';
export const name = KUBERNETES_V1_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACK_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACK_CONTROLLER, [SERVER_GROUP_WRITER]).controller(
  'kubernetesRollbackServerGroupController',
  [
    '$scope',
    '$uibModalInstance',
    'serverGroupWriter',
    'application',
    'serverGroup',
    'disabledServerGroups',
    function($scope, $uibModalInstance, serverGroupWriter, application, serverGroup, disabledServerGroups) {
      $scope.serverGroup = serverGroup;
      $scope.disabledServerGroups = disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
      $scope.verification = {};

      $scope.command = {
        rollbackType: 'EXPLICIT',
        rollbackContext: {
          rollbackServerGroupName: serverGroup.name,
        },
        namespace: serverGroup.namespace,
        interestingHealthProviderNames: ['KubernetesService'],
      };

      if (application && application.attributes) {
        $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
      }

      this.isValid = function() {
        const command = $scope.command;
        if (!$scope.verification.verified) {
          return false;
        }

        return command.rollbackContext.restoreServerGroupName !== undefined;
      };

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: 'Rollback ' + serverGroup.name,
        modalInstance: $uibModalInstance,
      });

      this.rollback = function() {
        if (!this.isValid()) {
          return;
        }

        const submitMethod = function() {
          return serverGroupWriter.rollbackServerGroup(serverGroup, application, $scope.command);
        };

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function() {
        $uibModalInstance.dismiss();
      };
    },
  ],
);
