'use strict';

import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { ConfirmationModalService, SETTINGS } from '@spinnaker/core';

import { GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE } from './../../../autoscalingPolicy/autoscalingPolicy.write.service';
import { GCEProviderSettings } from '../../../gce.settings';
import { GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER } from './modal/upsertAutoscalingPolicy.modal.controller';

export const GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE =
  'spinnaker.gce.instance.details.scalingPolicy.directive';
export const name = GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE; // for backwards compatibility
module(GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE, [
  ANGULAR_UI_BOOTSTRAP,
  GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE,
  GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER,
]).component('gceAutoscalingPolicy', {
  bindings: {
    policy: '=',
    application: '=',
    serverGroup: '=',
  },
  templateUrl: require('./autoscalingPolicy.directive.html'),
  controller: [
    '$uibModal',
    'gceAutoscalingPolicyWriter',
    function ($uibModal, gceAutoscalingPolicyWriter) {
      const policy = this.policy;

      policy.bases = [];

      if (policy.cpuUtilization) {
        const basis = {
          description: 'CPU Usage',
          helpKey: 'gce.serverGroup.autoscaling.targetCPUUsage',
        };

        if (policy.cpuUtilization.utilizationTarget) {
          basis.targets = [Math.round(policy.cpuUtilization.utilizationTarget * 100) + '%'];
        }

        policy.bases.push(basis);
      }

      if (policy.loadBalancingUtilization) {
        const basis = {
          description: 'HTTP Load Balancing Usage',
          helpKey: 'gce.serverGroup.autoscaling.targetHTTPLoadBalancingUsage',
        };

        if (policy.loadBalancingUtilization.utilizationTarget) {
          basis.targets = [Math.round(policy.loadBalancingUtilization.utilizationTarget * 100) + '%'];
        }

        policy.bases.push(basis);
      }

      if (policy.customMetricUtilizations) {
        const basis = {
          description: policy.customMetricUtilizations.length > 1 ? 'Monitoring Metrics' : 'Monitoring Metric',
          helpKey: 'gce.serverGroup.autoscaling.targetMetric',
        };

        if (policy.customMetricUtilizations.length > 0) {
          basis.targets = [];
          policy.customMetricUtilizations.forEach((metric) => {
            let target = metric.metric + ': ' + metric.utilizationTarget;

            if (metric.utilizationTargetType === 'DELTA_PER_SECOND') {
              target += '/sec';
            } else if (metric.utilizationTargetType === 'DELTA_PER_MINUTE') {
              target += '/min';
            }

            basis.targets.push(target);
          });
        }

        policy.bases.push(basis);
      }

      this.scaleInControlsConfigured =
        policy.scaleInControl &&
        policy.scaleInControl.timeWindowSec &&
        policy.scaleInControl.maxScaledInReplicas &&
        (policy.scaleInControl.maxScaledInReplicas.percent || policy.scaleInControl.maxScaledInReplicas.fixed);

      if (this.scaleInControlsConfigured) {
        this.maxScaledInReplicasMessage = policy.scaleInControl.maxScaledInReplicas.percent
          ? `${policy.scaleInControl.maxScaledInReplicas.percent}%`
          : `${policy.scaleInControl.maxScaledInReplicas.fixed}`;

        this.timeWindowSecMessage = `${policy.scaleInControl.timeWindowSec} seconds`;
      }

      this.predictiveAutoscalingEnabled =
        GCEProviderSettings.feature.predictiveAutoscaling &&
        policy.cpuUtilization &&
        policy.cpuUtilization.predictiveMethod;

      this.editPolicy = () => {
        $uibModal.open({
          templateUrl: require('./modal/upsertAutoscalingPolicy.modal.html'),
          controller: 'gceUpsertAutoscalingPolicyModalCtrl',
          controllerAs: 'ctrl',
          size: 'lg',
          resolve: {
            policy: () => this.policy,
            application: () => this.application,
            serverGroup: () => this.serverGroup,
          },
        });
      };

      this.deletePolicy = () => {
        const taskMonitor = {
          application: this.application,
          title: `Deleting autoscaler for ${this.serverGroup.name}`,
        };

        ConfirmationModalService.confirm({
          header: `Really delete autoscaler for ${this.serverGroup.name}?`,
          buttonText: 'Delete autoscaler',
          account: this.serverGroup.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: () => gceAutoscalingPolicyWriter.deleteAutoscalingPolicy(this.application, this.serverGroup),
        });
      };
    },
  ],
});
