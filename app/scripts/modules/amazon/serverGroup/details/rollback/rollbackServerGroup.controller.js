'use strict';

let angular = require('angular');

import {SERVER_GROUP_WRITER} from 'core/serverGroup/serverGroupWriter.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular.module('spinnaker.amazon.serverGroup.details.rollback.controller', [
      require('core/application/modal/platformHealthOverride.directive.js'),
      SERVER_GROUP_WRITER,
      TASK_MONITOR_BUILDER,
      require('../../../common/footer.directive.js'),
    ])
    .controller('awsRollbackServerGroupCtrl', function ($scope, $uibModalInstance, serverGroupWriter,
                                                        taskMonitorBuilder,
                                                        application, serverGroup, disabledServerGroups) {
      $scope.serverGroup = serverGroup;
      $scope.disabledServerGroups = disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
      $scope.verification = {};

      $scope.command = {
        rollbackType: 'EXPLICIT',
        rollbackContext: {
          rollbackServerGroupName: serverGroup.name
        }
      };

      if (application && application.attributes) {
        if (application.attributes.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
          $scope.command.interestingHealthProviderNames = ['Amazon'];
        }

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
