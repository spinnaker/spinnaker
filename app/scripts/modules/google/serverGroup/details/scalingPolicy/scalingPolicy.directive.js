'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.instance.details.scalingPolicy.directive', [
  require('../../../../core/insight/insightFilterState.model.js'),
])
  .directive('gceScalingPolicy', function(InsightFilterStateModel) {
    return {
      restrict: 'E',
      scope: {
        policy: '='
      },
      templateUrl: require('./scalingPolicy.directive.html'),
      link: function(scope) {
        var policy = scope.policy;
        scope.InsightFilterStateModel = InsightFilterStateModel;

        policy.bases = [];

        if (policy.cpuUtilization) {
          let basis = {
            description: 'CPU Usage',
            helpKey: 'gce.serverGroup.autoscaling.targetCPUUsage',
          };

          if (policy.cpuUtilization.utilizationTarget) {
            basis.targets = [policy.cpuUtilization.utilizationTarget * 100 + '%'];
          }

          policy.bases.push(basis);
        }

        if (policy.loadBalancingUtilization) {
          let basis = {
            description: 'HTTP Load Balancing Usage',
            helpKey: 'gce.serverGroup.autoscaling.targetHTTPLoadBalancingUsage',
          };

          if (policy.loadBalancingUtilization.utilizationTarget) {
            basis.targets = [policy.loadBalancingUtilization.utilizationTarget * 100 + '%'];
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
      }
    };
  });
