'use strict';

const angular = require('angular');

import { TaskMonitor } from '@spinnaker/core';

import './upsertAutoscalingPolicy.modal.less';

export const GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER =
  'spinnaker.deck.gce.upsertAutoscalingPolicy.modal.controller';
export const name = GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER, [
    require('google/autoscalingPolicy/autoscalingPolicy.write.service').name,
    require('google/autoscalingPolicy/components/basicSettings/basicSettings.component').name,
    require('google/autoscalingPolicy/components/metricSettings/metricSettings.component').name,
  ])
  .controller('gceUpsertAutoscalingPolicyModalCtrl', [
    'policy',
    'application',
    'serverGroup',
    '$uibModalInstance',
    'gceAutoscalingPolicyWriter',
    '$scope',
    function(policy, application, serverGroup, $uibModalInstance, gceAutoscalingPolicyWriter, $scope) {
      [this.action, this.isNew] = policy ? ['Edit', false] : ['New', true];
      this.policy = _.cloneDeep(policy || {});

      this.cancel = $uibModalInstance.dismiss;

      this.taskMonitor = new TaskMonitor({
        application: application,
        title: `${this.action} scaling policy for ${serverGroup.name}`,
        modalInstance: $uibModalInstance,
      });

      this.save = () => {
        const submitMethod = () =>
          gceAutoscalingPolicyWriter.upsertAutoscalingPolicy(application, serverGroup, this.policy);

        this.taskMonitor.submit(submitMethod);
      };

      this.updatePolicy = updatedPolicy => {
        $scope.$applyAsync(() => {
          this.policy = updatedPolicy;
        });
      };
    },
  ]);
