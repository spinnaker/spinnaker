'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.serverGroup.details.resize.controller', [
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/task/modal/reason.directive.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js')
])
  .controller('kubernetesResizeServerGroupController', function($scope, $uibModalInstance, serverGroupWriter, taskMonitorService,
                                                                application, serverGroup, kubernetesAutoscalerWriter) {
    $scope.serverGroup = serverGroup;
    $scope.currentSize = { desired: serverGroup.replicas };

    $scope.command = {
      capacity: {
        desired: $scope.currentSize.desired,
      },
    };

    if ($scope.serverGroup.autoscalerStatus) {
      $scope.command.capacity.min = $scope.serverGroup.deployDescription.capacity.min;
      $scope.command.capacity.max = $scope.serverGroup.deployDescription.capacity.max;
      $scope.command.scalingPolicy = { cpuUtilization: { target: null, }, };
    }

    $scope.verification = {};

    if (application && application.attributes) {
      if (application.attributes.platformHealthOnly) {
        $scope.command.interestingHealthProviderNames = ['Kubernetes'];
      }

      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function () {
      var command = $scope.command;
      return $scope.verification.verified
        && command.capacity !== null
        && command.capacity.desired !== null;
    };

    this.resize = function () {
      if (!this.isValid()) {
        return;
      }
      var capacity = $scope.command.capacity;

      var submitMethod = function() {
        var payload = {
          capacity: capacity,
          serverGroupName: serverGroup.name,
          credentials: serverGroup.account,
          account: serverGroup.account,
          namespace: serverGroup.region,
          region: serverGroup.region,
          interestingHealthProviderNames: ['KubernetesPod'],
          reason: $scope.command.reason,
        };
        if (serverGroup.autoscalerStatus) {
          return kubernetesAutoscalerWriter.upsertAutoscaler(serverGroup, application, payload);
        } else {
          return serverGroupWriter.resizeServerGroup(serverGroup, application, payload);
        }
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
