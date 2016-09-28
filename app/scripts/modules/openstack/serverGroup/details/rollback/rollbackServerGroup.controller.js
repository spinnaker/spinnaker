'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.details.rollback.controller', [
      require('../../../../core/application/modal/platformHealthOverride.directive.js'),
      require('../../../../core/serverGroup/serverGroup.write.service.js'),
      require('../../../../core/task/monitor/taskMonitorService.js'),
      require('../../../common/footer.directive.js'),
    ])
    .controller('openstackRollbackServerGroupCtrl', function ($scope, $uibModalInstance, serverGroupWriter,
                                                        taskMonitorService,
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
        $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
      }

      this.isValid = function () {
        var command = $scope.command;
        if (!$scope.verification.verified) {
          return false;
        }

        return command.rollbackContext.restoreServerGroupName !== undefined;
      };

      this.rollback = function () {
        if (!this.isValid()) {
          return;
        }

        var submitMethod = function () {
          return serverGroupWriter.rollbackServerGroup(serverGroup, application, $scope.command);
        };

        var taskMonitorConfig = {
          modalInstance: $uibModalInstance,
          application: application,
          title: 'Rollback ' + serverGroup.name,
        };

        $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function () {
        $uibModalInstance.dismiss();
      };
    });
