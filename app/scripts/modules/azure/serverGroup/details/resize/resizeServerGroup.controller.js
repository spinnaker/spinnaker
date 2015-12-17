'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.details.resize.controller', [
  require('../../../../core/account/account.service.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js')
])
  .controller('azureResizeServerGroupCtrl', function($scope, $modalInstance, accountService, serverGroupWriter, taskMonitorService,
                                                application, serverGroup) {
    $scope.serverGroup = serverGroup;
    $scope.currentSize = {
      min: serverGroup.asg.minSize,
      max: serverGroup.asg.maxSize,
      desired: serverGroup.asg.desiredCapacity,
      newSize: null
    };

    $scope.verification = {
      required: accountService.challengeDestructiveActions(serverGroup.account)
    };

    $scope.command = angular.copy($scope.currentSize);
    $scope.command.advancedMode = serverGroup.asg.minSize !== serverGroup.asg.maxSize;

    this.isValid = function () {
      var command = $scope.command;
      if ($scope.verification.required && $scope.verification.verifyAccount !== serverGroup.account.toUpperCase()) {
        return false;
      }
      return command.advancedMode ?
        command.min <= command.max && command.desired >= command.min && command.desired <= command.max :
        command.newSize !== null;
    };

    this.resize = function () {
      if (!this.isValid()) {
        return;
      }
      var capacity = { min: $scope.command.min, max: $scope.command.max, desired: $scope.command.desired };
      if (!$scope.command.advancedMode) {
        capacity = { min: $scope.command.newSize, max: $scope.command.newSize, desired: $scope.command.newSize };
      }

      var submitMethod = function() {
        return serverGroupWriter.resizeServerGroup(serverGroup, application, {capacity: capacity});
      };

      var taskMonitorConfig = {
        modalInstance: $modalInstance,
        application: application,
        title: 'Resizing ' + serverGroup.name,
        submitMethod: submitMethod
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
