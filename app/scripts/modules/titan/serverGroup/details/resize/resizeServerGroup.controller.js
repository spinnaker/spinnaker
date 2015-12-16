'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titan.serverGroup.details.resize.controller', [
  require('../../../../core/account/account.service.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js')
])
  .controller('titanResizeServerGroupCtrl', function($scope, $modalInstance, accountService, serverGroupWriter, taskMonitorService,
                                                application, serverGroup) {
    $scope.serverGroup = serverGroup;
    $scope.currentSize = {
      min: serverGroup.capacity.min,
      max: serverGroup.capacity.max,
      desired: serverGroup.capacity.desired,
      newSize: null
    };

    $scope.verification = {
      required: false,
      verifyAccount: '',
    };

    accountService.challengeDestructiveActions(serverGroup.account).then((challenge) => {
      $scope.verification.required = challenge;
    });


    $scope.command = angular.copy($scope.currentSize);
    $scope.command.advancedMode = serverGroup.capacity.min !== serverGroup.capacity.max;

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
        return serverGroupWriter.resizeServerGroup(serverGroup, application, {
          capacity: capacity,
          serverGroupName: serverGroup.name,
          instances: capacity.desired,
          region: serverGroup.region,
        });
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
