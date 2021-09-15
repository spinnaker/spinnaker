'use strict';

import { module } from 'angular';

import { ConfirmationModalService, ReactModal } from '@spinnaker/core';

import { ScalingPolicyWriter } from './ScalingPolicyWriter';
import { SCALING_POLICY_POPOVER } from './popover/scalingPolicyPopover.component';
import { UpsertScalingPolicyModal } from './upsert/UpsertScalingPolicyModal';

import './scalingPolicySummary.component.less';

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.alarmBasedSummary.component';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT; // for backwards compatibility
module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARMBASEDSUMMARY_COMPONENT, [SCALING_POLICY_POPOVER]).component(
  'alarmBasedSummary',
  {
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
          const upsertProps = {
            app: this.application,
            policy: this.policy,
            serverGroup: this.serverGroup,
          };
          const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
          ReactModal.show(UpsertScalingPolicyModal, upsertProps, modalProps);
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
  },
);
