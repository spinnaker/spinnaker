'use strict';

import { module } from 'angular';
import { get, set } from 'lodash';

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

export const KUBERNETES_V1_SERVERGROUP_DETAILS_RESIZE_RESIZE_CONTROLLER =
  'spinnaker.kubernetes.serverGroup.details.resize.controller';
export const name = KUBERNETES_V1_SERVERGROUP_DETAILS_RESIZE_RESIZE_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_SERVERGROUP_DETAILS_RESIZE_RESIZE_CONTROLLER, [SERVER_GROUP_WRITER]).controller(
  'kubernetesResizeServerGroupController',
  [
    '$scope',
    '$uibModalInstance',
    'serverGroupWriter',
    'application',
    'serverGroup',
    'kubernetesAutoscalerWriter',
    function($scope, $uibModalInstance, serverGroupWriter, application, serverGroup, kubernetesAutoscalerWriter) {
      $scope.serverGroup = serverGroup;
      $scope.currentSize = { desired: serverGroup.replicas };
      const hasAutoscaler = !!$scope.serverGroup.autoscalerStatus;

      $scope.command = {
        capacity: {
          desired: $scope.currentSize.desired,
        },
      };

      if (hasAutoscaler) {
        $scope.command.capacity.min = $scope.serverGroup.deployDescription.capacity.min;
        $scope.command.capacity.max = $scope.serverGroup.deployDescription.capacity.max;
        const cpuUtilizationTarget = get(
          $scope.serverGroup,
          'deployDescription.scalingPolicy.cpuUtilization.target',
          null,
        );
        set($scope.command, 'scalingPolicy.cpuUtilization.target', cpuUtilizationTarget);
      }

      $scope.verification = {};

      if (application && application.attributes) {
        if (application.attributes.platformHealthOnly) {
          $scope.command.interestingHealthProviderNames = ['Kubernetes'];
        }

        $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
      }

      this.isValid = function() {
        const command = $scope.command;
        return $scope.verification.verified && command.capacity !== null && command.capacity.desired !== null;
      };

      $scope.taskMonitor = new TaskMonitor({
        application: application,
        title: 'Resizing ' + serverGroup.name,
        modalInstance: $uibModalInstance,
      });

      this.resize = function() {
        if (!this.isValid()) {
          return;
        }
        let capacity;
        if (hasAutoscaler) {
          capacity = $scope.command.capacity;
        } else {
          capacity = {
            min: $scope.command.capacity.desired,
            max: $scope.command.capacity.desired,
            desired: $scope.command.capacity.desired,
          };
        }

        const submitMethod = function() {
          const payload = {
            capacity: capacity,
            scalingPolicy: hasAutoscaler ? $scope.command.scalingPolicy : null,
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

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function() {
        $uibModalInstance.dismiss();
      };
    },
  ],
);
