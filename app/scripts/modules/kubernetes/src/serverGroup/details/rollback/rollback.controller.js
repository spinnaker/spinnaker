'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE, SERVER_GROUP_WRITER, TASK_MONITOR_BUILDER } from '@spinnaker/core';

module.exports = angular.module('spinnaker.kubernetes.serverGroup.details.rollback.controller', [
      ACCOUNT_SERVICE,
      SERVER_GROUP_WRITER,
      TASK_MONITOR_BUILDER,
    ])
    .controller('kubernetesRollbackServerGroupController', function ($scope, $uibModalInstance, serverGroupWriter,
                                                                     taskMonitorBuilder,
                                                                     application, serverGroup, disabledServerGroups) {
      $scope.serverGroup = serverGroup;
      $scope.disabledServerGroups = disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
      $scope.verification = {};

      $scope.command = {
        rollbackType: 'EXPLICIT',
        rollbackContext: {
          rollbackServerGroupName: serverGroup.name
        },
        namespace: serverGroup.namespace,
        interestingHealthProviderNames: ['KubernetesService']
      };

      if (application && application.attributes) {
        $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
      }

      this.isValid = function () {
        var command = $scope.command;
        if (!$scope.verification.verified) {
          return false;
        }

        return command.rollbackContext.restoreServerGroupName !== undefined;
      };

      $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
        application: application,
        title: 'Rollback ' + serverGroup.name,
        modalInstance: $uibModalInstance,
      });

      this.rollback = function () {
        if (!this.isValid()) {
          return;
        }

        var submitMethod = function () {
          return serverGroupWriter.rollbackServerGroup(serverGroup, application, $scope.command);
        };

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function () {
        $uibModalInstance.dismiss();
      };
    });
