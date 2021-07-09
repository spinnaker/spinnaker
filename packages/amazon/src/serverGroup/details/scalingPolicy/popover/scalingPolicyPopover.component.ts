import { IComponentOptions, module } from 'angular';
import { AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_CHART_METRICALARMCHART_COMPONENT } from '../chart/MetricAlarmChart';

const scalingPolicyPopover: IComponentOptions = {
  bindings: {
    policy: '=',
    serverGroup: '=',
  },
  templateUrl: require('./scalingPolicyPopover.component.html'),
  controller() {
    this.$onInit = () => {
      this.alarm = this.policy.alarms[0];

      let showWait = false;
      if (this.policy.cooldown) {
        showWait = true;
      }
      if (this.policy.stepAdjustments && this.policy.stepAdjustments.length) {
        showWait = this.policy.stepAdjustments[0].operator !== 'decrease';
      }
      this.showWait = showWait;
    };
  },
};

export const SCALING_POLICY_POPOVER = 'spinnaker.amazon.serverGroup.details.scalingPolicy.popover.component';
module(SCALING_POLICY_POPOVER, [AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_CHART_METRICALARMCHART_COMPONENT]).component(
  'awsScalingPolicyPopover',
  scalingPolicyPopover,
);
