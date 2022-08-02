/* eslint-disable no-debugger */
import { IComponentOptions, module } from 'angular';
import { cloneDeep } from 'lodash';

import { TaskMonitor } from '@spinnaker/core';

import { GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_BASICSETTINGS_BASICSETTINGS_COMPONENT } from '../../../../autoscalingPolicy/components/basicSettings/basicSettings.component';
import { GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_METRICSETTINGS_METRICSETTINGS_COMPONENT } from '../../../../autoscalingPolicy/components/metricSettings/metricSettings.component';
import { GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_SCALINGSCHEDULES_SCALINGSCHEDULES_COMPONENT } from '../../../../autoscalingPolicy/components/scalingSchedules/scalingSchedules.component';

import './autoScalingPolicySelector.less';

const gceAutoScalingPolicySelectorComponent = {
  bindings: {
    policy: '<',
    enabled: '<',
    setAutoScalingPolicy: '&',
    autoscalingPolicy: '<',
  },
  templateUrl: require('./autoScalingPolicySelector.component.html'),
  controller: 'gceUpsertAutoscalingPolicyCtrl',
};

export const GCE_AUTOSCALING_POLICY_SELECTOR_COMPONENT = 'spinnaker.gce.autoScalingPolicy.selector.component';
module(GCE_AUTOSCALING_POLICY_SELECTOR_COMPONENT, [
  GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_BASICSETTINGS_BASICSETTINGS_COMPONENT,
  GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_METRICSETTINGS_METRICSETTINGS_COMPONENT,
  GOOGLE_AUTOSCALINGPOLICY_COMPONENTS_SCALINGSCHEDULES_SCALINGSCHEDULES_COMPONENT,
])
  .controller('gceUpsertAutoscalingPolicyCtrl', [
    '$scope',
    function ($scope) {
      var vm = this;
      this.$onInit = function () {
        this.policy = cloneDeep({});
        this.setAutoScalingPolicy({ autoscalingPolicy: this.policy });

        this.updatePolicy = (updatedPolicy) => {
          $scope.$applyAsync(() => {
            this.policy = updatedPolicy;
            this.setAutoScalingPolicy({ autoscalingPolicy: this.policy });
          });
        };
      };
    },
  ])
  .component('gceAutoScalingPolicySelectorComponent', gceAutoScalingPolicySelectorComponent);
