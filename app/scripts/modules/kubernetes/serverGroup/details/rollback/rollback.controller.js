'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.kubernetes.serverGroup.details.rollback.controller', [
      ACCOUNT_SERVICE,
      require('core/application/modal/platformHealthOverride.directive.js'),
      require('core/task/modal/reason.directive.js'),
      require('core/serverGroup/serverGroup.write.service.js'),
      require('core/task/monitor/taskMonitorService.js'),
    ])
    .controller('kubernetesRollbackServerGroupController', function ($scope, $uibModalInstance, serverGroupWriter,
                                                                     taskMonitorService,
                                                                     application, serverGroup, disabledServerGroups) {
      $scope.serverGroup = serverGroup;
      $scope.disabledServerGroups = disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
      $scope.verification = {};

      $scope.command = {
        rollbackType: 'EXPLICIT',
        rollbackContext: {
          rollbackServerGroupName: serverGroup.name
        },
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

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Rollback ' + serverGroup.name,
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
