import { IComponentOptions, IController, module } from 'angular';

import { Application, IServerGroup } from '@spinnaker/core';
import { ITitusPolicy } from '../../../domain';

class ScalingPolicyDetailsSummaryController implements IController {
  public templateUrl: string;
  public policy: ITitusPolicy;
  public serverGroup: IServerGroup;
  public application: Application;

  public $onInit() {
    if (this.policy.targetPolicyDescriptor) {
      this.templateUrl = require('./targetTracking/targetTrackingSummary.html');
    } else {
      this.templateUrl = require('./alarmBasedSummary.template.html');
    }
  }
}

const component: IComponentOptions = {
  bindings: {
    policy: '<',
    serverGroup: '<',
    application: '<',
  },
  controller: ScalingPolicyDetailsSummaryController,
  template: `<div ng-include src="$ctrl.templateUrl"></div>`,
};

export const SCALING_POLICY_SUMMARY = 'spinnaker.titus.scalingPolicy.details.summary.component';
module(SCALING_POLICY_SUMMARY, []).component('titusScalingPolicySummary', component);
