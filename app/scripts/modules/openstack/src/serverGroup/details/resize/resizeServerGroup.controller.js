'use strict';

const angular = require('angular');

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.serverGroup.details.resize.controller', [SERVER_GROUP_WRITER])
  .controller('openstackResizeServerGroupCtrl', ['$scope', '$uibModalInstance', 'serverGroupWriter', 'application', 'serverGroup', function(
    $scope,
    $uibModalInstance,
    serverGroupWriter,
    application,
    serverGroup,
  ) {
    $scope.serverGroup = serverGroup;
    $scope.currentSize = {
      min: serverGroup.scalingConfig.minSize,
      max: serverGroup.scalingConfig.maxSize,
      desired: serverGroup.scalingConfig.desiredSize,
    };

    $scope.verification = {};

    $scope.command = {
      capacity: angular.copy($scope.currentSize),
      advancedMode: serverGroup.scalingConfig.min !== serverGroup.scalingConfig.max,
    };

    if (application && application.attributes) {
      if (application.attributes.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
        $scope.command.interestingHealthProviderNames = ['Openstack'];
      }

      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function() {
      var command = $scope.command;
      if (!$scope.verification.verified) {
        return false;
      }
      return command.advancedMode
        ? command.capacity.min <= command.capacity.max &&
            command.capacity.desired >= command.capacity.min &&
            command.capacity.desired <= command.capacity.max
        : command.capacity.desired !== null;
    };

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Resizing ' + serverGroup.name,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    this.resize = function() {
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

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };
  }]);
