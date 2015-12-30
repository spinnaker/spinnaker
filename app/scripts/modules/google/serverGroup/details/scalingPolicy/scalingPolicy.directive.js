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

        if (policy.cpuUtilization) {
          policy.basis = 'CPU Usage';
          policy.basisHelpKey = 'gce.serverGroup.autoscaling.targetCPUUsage';

          if (policy.cpuUtilization.utilizationTarget) {
            policy.targets = [policy.cpuUtilization.utilizationTarget * 100 + '%'];
          }
        } else if (policy.loadBalancingUtilization) {
          policy.basis = 'HTTP Load Balancing Usage';
          policy.basisHelpKey = 'gce.serverGroup.autoscaling.targetHTTPLoadBalancingUsage';

          if (policy.loadBalancingUtilization.utilizationTarget) {
            policy.targets = [policy.loadBalancingUtilization.utilizationTarget * 100 + '%'];
          }
        } else if (policy.customMetricUtilizations) {
          policy.basis = policy.customMetricUtilizations.length > 1 ? 'Monitoring Metrics' : 'Monitoring Metric';
          policy.basisHelpKey = 'gce.serverGroup.autoscaling.targetMetric';

          if (policy.customMetricUtilizations.length > 0) {
            policy.targets = [];
            policy.customMetricUtilizations.forEach(metric => {
              let target = metric.metric + ': ' + metric.utilizationTarget;

              if (metric.utilizationTargetType === 'DELTA_PER_SECOND') {
                target += '/sec';
              } else if (metric.utilizationTargetType === 'DELTA_PER_MINUTE') {
                target += '/min';
              }

              policy.targets.push(target);
            });
          }
        }
      }
    };
  });
