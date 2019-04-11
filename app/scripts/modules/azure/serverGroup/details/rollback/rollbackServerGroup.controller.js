'use strict';

const angular = require('angular');

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.serverGroup.details.rollback.controller', [
    SERVER_GROUP_WRITER,
    require('../../../common/footer.directive').name,
  ])
  .controller('azureRollbackServerGroupCtrl', [
    '$scope',
    '$uibModalInstance',
    'serverGroupWriter',
    'application',
    'serverGroup',
    'disabledServerGroups',
    function($scope, $uibModalInstance, serverGroupWriter, application, serverGroup, disabledServerGroups) {
      $scope.serverGroup = serverGroup;
      $scope.disabledServerGroups = disabledServerGroups
        .filter(disabledServerGroup => disabledServerGroup.instanceCounts.total !== 0)
        .sort((a, b) => b.name.localeCompare(a.name));
      $scope.verification = {};

      $scope.command = {
        rollbackType: 'EXPLICIT',
        rollbackContext: {
          rollbackServerGroupName: serverGroup.name,
          enableAndDisableOnly: true,
        },
      };

      this.isValid = function() {
        const command = $scope.command;
        if (!$scope.verification.verified) {
          return false;
        }

        return command.rollbackContext.restoreServerGroupName !== undefined;
      };

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: 'Rollback ' + serverGroup.name,
        modalInstance: $uibModalInstance,
      });

      this.rollback = function() {
        this.submitting = true;
        if (!this.isValid()) {
          return;
        }

        const submitMethod = function() {
          $scope.command.interestingHealthProviderNames = [];
          var restoreServerGroup = $scope.disabledServerGroups.find(function(disabledServerGroup) {
            return disabledServerGroup.name === $scope.command.rollbackContext.restoreServerGroupName;
          });
          $scope.command.targetSize = restoreServerGroup.capacity.max;
          return serverGroupWriter.rollbackServerGroup(serverGroup, application, $scope.command);
        };

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function() {
        $uibModalInstance.dismiss();
      };
    },
  ]);
