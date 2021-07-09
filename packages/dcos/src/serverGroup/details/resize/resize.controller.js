'use strict';

import * as angular from 'angular';

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

export const DCOS_SERVERGROUP_DETAILS_RESIZE_RESIZE_CONTROLLER = 'spinnaker.dcos.serverGroup.details.resize.controller';
export const name = DCOS_SERVERGROUP_DETAILS_RESIZE_RESIZE_CONTROLLER; // for backwards compatibility
angular
  .module(DCOS_SERVERGROUP_DETAILS_RESIZE_RESIZE_CONTROLLER, [SERVER_GROUP_WRITER])
  .controller('dcosResizeServerGroupController', [
    '$scope',
    '$uibModalInstance',
    'serverGroupWriter',
    'application',
    'serverGroup',
    function ($scope, $uibModalInstance, serverGroupWriter, application, serverGroup) {
      $scope.serverGroup = serverGroup;
      $scope.currentSize = {
        oldSize: serverGroup.instances.length,
        newSize: null,
      };

      $scope.verification = {};

      $scope.command = angular.copy($scope.currentSize);

      if (application && application.attributes) {
        $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
      }

      this.isValid = function () {
        const command = $scope.command;
        if (!$scope.verification.verified) {
          return false;
        }
        return command.newSize !== null;
      };

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: 'Resizing ' + serverGroup.name,
        modalInstance: $uibModalInstance,
      });

      this.resize = function () {
        if (!this.isValid()) {
          return;
        }

        const capacity = { min: $scope.command.newSize, max: $scope.command.newSize, desired: $scope.command.newSize };

        const submitMethod = function () {
          return serverGroupWriter.resizeServerGroup(serverGroup, application, {
            serverGroupName: serverGroup.name,
            credentials: serverGroup.account,
            account: serverGroup.account,
            region: serverGroup.region,
            dcosCluster: serverGroup.dcosCluster,
            group: serverGroup.group,
            capacity: capacity,
            targetSize: $scope.command.newSize,
            forceDeployment: $scope.command.forceDeployment,
          });
        };

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function () {
        $uibModalInstance.dismiss();
      };
    },
  ]);
