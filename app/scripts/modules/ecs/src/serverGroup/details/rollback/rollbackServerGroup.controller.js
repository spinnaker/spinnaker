'use strict';

const angular = require('angular');

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.serverGroup.details.rollback.controller', [SERVER_GROUP_WRITER])
  .controller('ecsRollbackServerGroupCtrl', [
    '$scope',
    '$uibModalInstance',
    'serverGroupWriter',
    'application',
    'serverGroup',
    'disabledServerGroups',
    'allServerGroups',
    function(
      $scope,
      $uibModalInstance,
      serverGroupWriter,
      application,
      serverGroup,
      disabledServerGroups,
      allServerGroups,
    ) {
      $scope.serverGroup = serverGroup;
      $scope.disabledServerGroups = disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
      $scope.allServerGroups = allServerGroups.sort((a, b) => b.name.localeCompare(a.name));
      $scope.verification = {};

      $scope.command = {
        rollbackType: 'EXPLICIT',
        rollbackContext: {
          rollbackServerGroupName: serverGroup.name,
        },
      };

      if (application && application.attributes) {
        if (application.attributes.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
          $scope.command.interestingHealthProviderNames = ['Ecs'];
        }

        $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
      }

      this.isValid = function() {
        var command = $scope.command;
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
        if (!this.isValid()) {
          return;
        }

        var submitMethod = function() {
          return serverGroupWriter.rollbackServerGroup(serverGroup, application, $scope.command);
        };

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function() {
        $uibModalInstance.dismiss();
      };

      this.label = function(serverGroup) {
        if (!serverGroup) {
          return '';
        }

        if (!serverGroup.buildInfo || !serverGroup.buildInfo.jenkins || !serverGroup.buildInfo.jenkins.number) {
          return serverGroup.name;
        }

        return serverGroup.name + ' (build #' + serverGroup.buildInfo.jenkins.number + ')';
      };

      this.group = function(serverGroup) {
        return serverGroup.isDisabled ? 'Disabled Server Groups' : 'Enabled Server Groups';
      };
    },
  ]);
