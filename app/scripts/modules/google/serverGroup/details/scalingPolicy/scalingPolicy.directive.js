'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.instance.details.scalingPolicy.directive', [
  require('../../../../core/insight/insightFilterState.model.js'),
  require('../../../../core/widgets/actionIcons/actionIcons.component.js'),
  require('../../../../core/confirmationModal/confirmationModal.service.js'),
  require('angular-ui-bootstrap'),
  require('./scalingPolicy.write.service.js'),
  require('./upsert/upsertScalingPolicy.controller.js')
])
  .component('gceScalingPolicy', {
    bindings: {
      policy: '=',
      application: '=',
      serverGroup: '='
    },
    templateUrl: require('./scalingPolicy.directive.html'),
    controller: function(InsightFilterStateModel, $uibModal, gceScalingPolicyWriter, confirmationModalService) {
      let policy = this.policy;
      this.InsightFilterStateModel = InsightFilterStateModel;

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
          templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
          controller: 'gceUpsertScalingPolicyCtrl',
          controllerAs: 'ctrl',
          size: 'lg',
          resolve: {
            policy: () => this.policy,
            application: () => this.application,
            serverGroup: () => this.serverGroup
          }
        });
      };

      this.deletePolicy = () => {
        let taskMonitor = {
          application: this.application,
          title: `Deleting autoscaler for ${this.serverGroup.name}`,
        };

        confirmationModalService.confirm({
          header: `Really delete autoscaler for ${this.serverGroup.name}?`,
          buttonText: 'Delete autoscaler',
          account: this.serverGroup.account,
          provider: 'gce',
          taskMonitorConfig: taskMonitor,
          submitMethod: () => gceScalingPolicyWriter.deleteScalingPolicy(this.application, this.serverGroup)
        });
      };
    }
  });
