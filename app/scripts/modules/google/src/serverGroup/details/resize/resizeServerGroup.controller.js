'use strict';

const angular = require('angular');

import { TaskMonitor } from '@spinnaker/core';

export const GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER =
  'spinnaker.google.serverGroup.details.resize.controller';
export const name = GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER, [
    require('./resizeCapacity.component').name,
    require('./resizeAutoscalingPolicy.component').name,
    require('../../../common/footer.directive').name,
  ])
  .controller('gceResizeServerGroupCtrl', [
    '$scope',
    '$uibModalInstance',
    'application',
    'serverGroup',
    function($scope, $uibModalInstance, application, serverGroup) {
      $scope.serverGroup = serverGroup;
      $scope.application = application;
      $scope.verification = {};
      $scope.command = {};
      $scope.formMethods = {};

      if (application && application.attributes) {
        if (application.attributes.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
          $scope.command.interestingHealthProviderNames = ['Google'];
        }

        $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
      }

      this.isValid = function() {
        if (!$scope.verification.verified) {
          return false;
        }
        return $scope.formMethods.formIsValid();
      };

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: 'Resizing ' + serverGroup.name,
        modalInstance: $uibModalInstance,
      });

      this.resize = function() {
        this.submitting = true;
        if (!this.isValid()) {
          return;
        }

        $scope.taskMonitor.submit($scope.formMethods.submitMethod);
      };

      this.cancel = function() {
        $uibModalInstance.dismiss();
      };
    },
  ]);
