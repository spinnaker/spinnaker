'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.amazon.serverGroup.details.rollback.controller', [
      require('../../../../core/account/account.service.js'),
      require('../../../../core/application/modal/platformHealthOverride.directive.js'),
      require('../../../../core/serverGroup/serverGroup.write.service.js'),
      require('../../../../core/task/monitor/taskMonitorService.js'),
      require('../../../common/footer.directive.js'),
      require('../../../common/verification.directive.js'),
    ])
    .controller('awsRollbackServerGroupCtrl', function ($scope, $modalInstance, accountService, serverGroupWriter,
                                                        taskMonitorService,
                                                        application, serverGroup, disabledServerGroups) {
      $scope.serverGroup = serverGroup;
      $scope.disabledServerGroups = disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
      $scope.verification = {
        required: accountService.challengeDestructiveActions('aws', serverGroup.account)
      };

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
        if ($scope.verification.required && $scope.verification.verifyAccount !== serverGroup.account.toUpperCase()) {
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
          modalInstance: $modalInstance,
          application: application,
          title: 'Rollback ' + serverGroup.name,
          submitMethod: submitMethod
        };

        $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function () {
        $modalInstance.dismiss();
      };
    });
