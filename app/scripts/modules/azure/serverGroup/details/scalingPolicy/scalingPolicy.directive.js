'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.instance.details.scalingPolicy.directive', [
  require('../../../../core/insight/insightFilterState.model.js'),
])
  .directive('azureScalingPolicy', function(InsightFilterStateModel) {
    return {
      restrict: 'E',
      scope: {
        policy: '='
      },
      templateUrl: require('./scalingPolicy.directive.html'),
      link: function(scope) {
        var policy = scope.policy;
        scope.InsightFilterStateModel = InsightFilterStateModel;
        policy.alarms = policy.alarms || [];

        function addComparator(alarm) {
          switch(alarm.comparisonOperator) {
            case 'LessThanThreshold':
              alarm.comparator = '<';
              break;
            case 'GreaterThanThreshold':
              alarm.comparator = '>';
              break;
            case 'LessThanOrEqualToThreshold':
              alarm.comparator = '<=';
              break;
            case 'GreaterThanOrEqualToThreshold':
              alarm.comparator = '>=';
              break;
          }
        }

        policy.operator  = policy.scalingAdjustment <= 0 ? 'decrease' : 'increase';
        policy.alarms.forEach(addComparator);
        policy.minAdjustmentStep = policy.minAdjustmentStep || 1;
        policy.absAdjustment = Math.abs(policy.scalingAdjustment);
      }
    };
  });
