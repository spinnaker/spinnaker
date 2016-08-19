'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.google.serverGroup.details.resize.controller', [
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/task/modal/reason.directive.js'),
  require('../../../../core/task/monitor/taskMonitorService.js'),
  require('./resizeCapacity.component.js'),
  require('./resizeAutoscalingPolicy.component.js'),
  require('../../../common/footer.directive.js'),
])
  .controller('gceResizeServerGroupCtrl', function($scope, $uibModalInstance, taskMonitorService,
                                                   application, serverGroup) {
    $scope.serverGroup = serverGroup;
    $scope.application = application;
    $scope.verification = {};
    $scope.command = {};
    $scope.formMethods = {};

    if (application && application.attributes) {
      if (application.attributes.platformHealthOnly) {
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

    this.resize = function () {
      this.submitting = true;
      if (!this.isValid()) {
        return;
      }

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Resizing ' + serverGroup.name,
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit($scope.formMethods.submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
