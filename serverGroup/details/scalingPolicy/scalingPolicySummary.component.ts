import { IController, IComponentOptions, module } from 'angular';

import { Application, IServerGroup } from '@spinnaker/core';

import { IScalingPolicy, ScalingPolicyTypeRegistry } from '@spinnaker/amazon';

class ScalingPolicyDetailsSummaryController implements IController {

  public templateUrl: string;
  public policy: IScalingPolicy;
  public serverGroup: IServerGroup;
  public application: Application;

  public $onInit() {
    const config = ScalingPolicyTypeRegistry.getPolicyConfig(this.policy.policyType);
    this.templateUrl = config ? config.summaryTemplateUrl : require('./alarmBasedSummary.template.html');
  }
}

const component: IComponentOptions = {
  bindings: {
    policy: '<',
    serverGroup: '<',
    application: '<',
  },
  controller: ScalingPolicyDetailsSummaryController,
  template: `<div ng-include src="$ctrl.templateUrl"></div>`
};

export const SCALING_POLICY_SUMMARY = 'spinnaker.titus.scalingPolicy.details.summary.component';
module(SCALING_POLICY_SUMMARY, []).component('titusScalingPolicySummary', component);
