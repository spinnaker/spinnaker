'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titus.serverGroup.details.resize.controller', [
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js')
])
  .controller('titusResizeServerGroupCtrl', function($scope, $uibModalInstance, serverGroupWriter, taskMonitorService,
                                                application, serverGroup) {
    $scope.serverGroup = serverGroup;
    $scope.currentSize = {
      min: serverGroup.capacity.min,
      max: serverGroup.capacity.max,
      desired: serverGroup.capacity.desired,
      newSize: null
    };

    $scope.verification = {};

    $scope.command = angular.copy($scope.currentSize);
    $scope.command.advancedMode = serverGroup.capacity.min !== serverGroup.capacity.max;

    if (application && application.attributes) {
      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function () {
      var command = $scope.command;
      if (!$scope.verification.verified) {
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
          interestingHealthProviderNames: $scope.command.interestingHealthProviderNames,
          region: serverGroup.region,
        });
      };

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Resizing ' + serverGroup.name,
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
