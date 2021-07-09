'use strict';

import { module } from 'angular';

import { ConfirmationModalService } from '@spinnaker/core';

import { ScalingPolicyWriter } from './ScalingPolicyWriter';
import { SCALING_POLICY_POPOVER } from './popover/scalingPolicyPopover.component';
import { AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_UPSERTSCALINGPOLICY_CONTROLLER } from './upsert/upsertScalingPolicy.controller';

import './scalingPolicySummary.component.less';

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.alarmBasedSummary.component';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT; // for backwards compatibility
module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT, [
  AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_UPSERTSCALINGPOLICY_CONTROLLER,
  SCALING_POLICY_POPOVER,
]).component('alarmBasedSummary', {
  bindings: {
    policy: '=',
    serverGroup: '=',
    application: '=',
  },
  templateUrl: require('./alarmBasedSummary.component.html'),
  controller: [
    '$uibModal',
    function ($uibModal) {
      this.popoverTemplate = require('./popover/scalingPolicyDetails.popover.html');

      this.editPolicy = () => {
        $uibModal.open({
          templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
          controller: 'awsUpsertScalingPolicyCtrl',
          controllerAs: 'ctrl',
          size: 'lg',
          resolve: {
            policy: () => this.policy,
            serverGroup: () => this.serverGroup,
            application: () => this.application,
          },
        });
      };

      this.deletePolicy = () => {
        const taskMonitor = {
          application: this.application,
          title: 'Deleting scaling policy ' + this.policy.policyName,
        };

        const submitMethod = () =>
          ScalingPolicyWriter.deleteScalingPolicy(this.application, this.serverGroup, this.policy);

        ConfirmationModalService.confirm({
          header: 'Really delete ' + this.policy.policyName + '?',
          buttonText: 'Delete scaling policy',
          account: this.policy.alarms.length ? this.serverGroup.account : null, // don't confirm if it's a junk policy
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };
    },
  ],
});
