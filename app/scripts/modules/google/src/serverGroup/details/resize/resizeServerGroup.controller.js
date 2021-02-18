'use strict';

import { module } from 'angular';

import { TaskMonitor } from '@spinnaker/core';

import { GOOGLE_COMMON_FOOTER_DIRECTIVE } from '../../../common/footer.directive';
import { GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZEAUTOSCALINGPOLICY_COMPONENT } from './resizeAutoscalingPolicy.component';
import { GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT } from './resizeCapacity.component';

export const GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER =
  'spinnaker.google.serverGroup.details.resize.controller';
export const name = GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER; // for backwards compatibility
module(GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER, [
  GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT,
  GOOGLE_SERVERGROUP_DETAILS_RESIZE_RESIZEAUTOSCALINGPOLICY_COMPONENT,
  GOOGLE_COMMON_FOOTER_DIRECTIVE,
]).controller('gceResizeServerGroupCtrl', [
  '$scope',
  '$uibModalInstance',
  'application',
  'serverGroup',
  function ($scope, $uibModalInstance, application, serverGroup) {
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

    this.isValid = function () {
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

    this.resize = function () {
      this.submitting = true;
      if (!this.isValid()) {
        return;
      }

      $scope.taskMonitor.submit($scope.formMethods.submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  },
]);
