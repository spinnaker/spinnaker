'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.details.resize.controller', [
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js')
])
  .controller('openstackResizeServerGroupCtrl', function($scope, $uibModalInstance, serverGroupWriter, taskMonitorService,
                                                   application, serverGroup) {

    $scope.serverGroup = serverGroup;
    $scope.currentSize = {
      min: serverGroup.scalingConfig.min,
      max: serverGroup.scalingConfig.max,
      desired: serverGroup.scalingConfig.desiredSize
    };

    $scope.verification = {};

    $scope.command = {
      capacity: angular.copy($scope.currentSize),
      advancedMode: serverGroup.scalingConfig.min !== serverGroup.scalingConfig.max
    };

    if (application && application.attributes) {
      if (application.attributes.platformHealthOnly) {
        $scope.command.interestingHealthProviderNames = ['OpenStack'];
      }

      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function () {
      var command = $scope.command;
      if (!$scope.verification.verified) {
        return false;
      }
      return command.advancedMode ?
        command.capacity.min <= command.capacity.max && command.capacity.desired >= command.capacity.min && command.capacity.desired <= command.capacity.max :
        command.capacity.desired !== null;
    };

    this.resize = function () {
      if (!this.isValid()) {
        return;
      }

      if (!$scope.command.advancedMode) {
        $scope.command.capacity.min = $scope.command.capacity.desired;
        $scope.command.capacity.max = $scope.command.capacity.desired;
      }

      var submitMethod = function() {
        return serverGroupWriter.resizeServerGroup(serverGroup, application, {
          capacity: $scope.command.capacity,
          serverGroupName: serverGroup.name,
          targetSize: $scope.command.capacity.desired,
          region: serverGroup.region,
          interestingHealthProviderNames: $scope.command.interestingHealthProviderNames,
        });
      };

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Resizing ' + serverGroup.name,
        onTaskComplete: () => application.serverGroups.refresh(),
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
