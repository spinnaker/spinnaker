'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.serverGroup.details.resize.controller', [
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js')
])
  .controller('cfResizeServerGroupCtrl', function($scope, $uibModalInstance, serverGroupWriter, taskMonitorService,
                                                   application, serverGroup) {

    // TODO: Rip out min/max and just use desired capacity.
    $scope.serverGroup = serverGroup;
    $scope.currentSize = {
      min: serverGroup.asg.minSize,
      max: serverGroup.asg.maxSize,
      desired: serverGroup.asg.desiredCapacity,
      newSize: null
    };

    $scope.verification = {};

    $scope.command = angular.copy($scope.currentSize);
    $scope.command.advancedMode = serverGroup.asg.minSize !== serverGroup.asg.maxSize;

    if (application && application.attributes) {
      if (application.attributes.platformHealthOnly) {
        $scope.command.interestingHealthProviderNames = ['Cloud Foundry'];
      }

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
          targetSize: capacity.desired, // TODO(GLT): Unify on this or capacity
          region: serverGroup.region,
          interestingHealthProviderNames: $scope.command.interestingHealthProviderNames,
        });
      };

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Resizing ' + serverGroup.name,
        onTaskComplete: application.serverGroups.refresh,
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
