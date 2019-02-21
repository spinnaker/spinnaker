'use strict';

const angular = require('angular');

import { CONFIRMATION_MODAL_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.gce.instance.details.scalingPolicy.directive', [
    CONFIRMATION_MODAL_SERVICE,
    require('angular-ui-bootstrap'),
    require('./../../../autoscalingPolicy/autoscalingPolicy.write.service').name,
    require('./modal/upsertAutoscalingPolicy.modal.controller').name,
  ])
  .component('gceAutoscalingPolicy', {
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
          let basis = {
            description: 'CPU Usage',
            helpKey: 'gce.serverGroup.autoscaling.targetCPUUsage',
          };

          if (policy.cpuUtilization.utilizationTarget) {
            basis.targets = [Math.round(policy.cpuUtilization.utilizationTarget * 100) + '%'];
          }

          policy.bases.push(basis);
        }

        if (policy.loadBalancingUtilization) {
          let basis = {
            description: 'HTTP Load Balancing Usage',
            helpKey: 'gce.serverGroup.autoscaling.targetHTTPLoadBalancingUsage',
          };

          if (policy.loadBalancingUtilization.utilizationTarget) {
            basis.targets = [Math.round(policy.loadBalancingUtilization.utilizationTarget * 100) + '%'];
          }

          policy.bases.push(basis);
        }

        if (policy.customMetricUtilizations) {
          let basis = {
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
