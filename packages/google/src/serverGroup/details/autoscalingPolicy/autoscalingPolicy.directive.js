'use strict';

import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { ConfirmationModalService, SETTINGS } from '@spinnaker/core';

import { GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE } from './../../../autoscalingPolicy/autoscalingPolicy.write.service';
import { GCE_AUTOSCALING_POLICY_SELECTOR_COMPONENT } from '../../configure/wizard/autoScalingPolicy/autoScalingPolicySelector.component';
import { GCEProviderSettings } from '../../../gce.settings';
import { GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER } from './modal/upsertAutoscalingPolicy.modal.controller';
export const GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE =
  'spinnaker.gce.instance.details.scalingPolicy.directive';
export const name = GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE; // for backwards compatibility
module(GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_DIRECTIVE, [
  ANGULAR_UI_BOOTSTRAP,
  GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE,
  GOOGLE_SERVERGROUP_DETAILS_AUTOSCALINGPOLICY_MODAL_UPSERTAUTOSCALINGPOLICY_MODAL_CONTROLLER,
  GCE_AUTOSCALING_POLICY_SELECTOR_COMPONENT,
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
      var scope = this;
      scope.$onInit = function () {
        const policy = scope.policy;

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

        scope.scaleInControlsConfigured =
          policy.scaleInControl &&
          policy.scaleInControl.timeWindowSec &&
          policy.scaleInControl.maxScaledInReplicas &&
          (policy.scaleInControl.maxScaledInReplicas.percent || policy.scaleInControl.maxScaledInReplicas.fixed);

        if (scope.scaleInControlsConfigured) {
          scope.maxScaledInReplicasMessage = policy.scaleInControl.maxScaledInReplicas.percent
            ? `${policy.scaleInControl.maxScaledInReplicas.percent}%`
            : `${policy.scaleInControl.maxScaledInReplicas.fixed}`;

          scope.timeWindowSecMessage = `${policy.scaleInControl.timeWindowSec} seconds`;
        }

        scope.predictiveAutoscalingEnabled =
          GCEProviderSettings.feature.predictiveAutoscaling &&
          policy.cpuUtilization &&
          policy.cpuUtilization.predictiveMethod;

        scope.editPolicy = () => {
          $uibModal.open({
            templateUrl: require('./modal/upsertAutoscalingPolicy.modal.html'),
            controller: 'gceUpsertAutoscalingPolicyModalCtrl',
            controllerAs: 'ctrl',
            size: 'lg',
            resolve: {
              policy: () => scope.policy,
              application: () => scope.application,
              serverGroup: () => scope.serverGroup,
            },
          });
        };

        scope.deletePolicy = () => {
          const taskMonitor = {
            application: scope.application,
            title: `Deleting autoscaler for ${scope.serverGroup.name}`,
          };

          ConfirmationModalService.confirm({
            header: `Really delete autoscaler for ${scope.serverGroup.name}?`,
            buttonText: 'Delete autoscaler',
            account: scope.serverGroup.account,
            taskMonitorConfig: taskMonitor,
            submitMethod: () =>
              gceAutoscalingPolicyWriter.deleteAutoscalingPolicy(scope.application, scope.serverGroup),
          });
        };
      };
    },
  ],
});
