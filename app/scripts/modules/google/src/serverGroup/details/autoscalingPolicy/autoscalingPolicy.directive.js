'use strict';

import { module } from 'angular';

import { CONFIRMATION_MODAL_SERVICE, SETTINGS } from '@spinnaker/core';
import { GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE } from './../../../autoscalingPolicy/autoscalingPolicy.write.service';
import { GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER } from './modal/upsertAutoscalingPolicy.modal.controller';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

export const GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE =
  'spinnaker.gce.instance.details.scalingPolicy.directive';
export const name = GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE; // for backwards compatibility
module(GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE, [
  CONFIRMATION_MODAL_SERVICE,
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
    'confirmationModalService',
    function($uibModal, gceAutoscalingPolicyWriter, confirmationModalService) {
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
          policy.customMetricUtilizations.forEach(metric => {
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

      this.scaleDownControlsEnabled = SETTINGS.feature.gceScaleDownControlsEnabled;
      this.scaleDownControlsConfigured =
        this.scaleDownControlsEnabled &&
        policy.scaleDownControl &&
        policy.scaleDownControl.timeWindowSec &&
        policy.scaleDownControl.maxScaledDownReplicas &&
        (policy.scaleDownControl.maxScaledDownReplicas.percent || policy.scaleDownControl.maxScaledDownReplicas.fixed);

      if (this.scaleDownControlsConfigured) {
        this.maxScaledDownReplicasMessage = policy.scaleDownControl.maxScaledDownReplicas.percent
          ? `${policy.scaleDownControl.maxScaledDownReplicas.percent}%`
          : `${policy.scaleDownControl.maxScaledDownReplicas.fixed}`;

        this.timeWindowSecMessage = `${policy.scaleDownControl.timeWindowSec} seconds`;
      }

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

        confirmationModalService.confirm({
          header: `Really delete autoscaler for ${this.serverGroup.name}?`,
          buttonText: 'Delete autoscaler',
          account: this.serverGroup.account,
          provider: 'gce',
          taskMonitorConfig: taskMonitor,
          submitMethod: () => gceAutoscalingPolicyWriter.deleteAutoscalingPolicy(this.application, this.serverGroup),
        });
      };
    },
  ],
});
