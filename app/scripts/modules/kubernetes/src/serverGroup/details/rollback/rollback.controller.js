'use strict';

const angular = require('angular');

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.details.rollback.controller', [SERVER_GROUP_WRITER])
  .controller('kubernetesRollbackServerGroupController', [
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
        var command = $scope.command;
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

        var submitMethod = function() {
          return serverGroupWriter.rollbackServerGroup(serverGroup, application, $scope.command);
        };

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function() {
        $uibModalInstance.dismiss();
      };
    },
  ]);
