'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.instance.details.scalingPolicy.directive', [
  require('../../../../core/insight/insightFilterState.model.js'),
])
  .directive('scalingPolicy', function(InsightFilterStateModel) {
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

        function addAdjustmentAttributes(adjuster) {
          adjuster.operator  = adjuster.scalingAdjustment <= 0 ? 'decrease' : 'increase';
          adjuster.absAdjustment = Math.abs(adjuster.scalingAdjustment);
        }

        policy.alarms.forEach(addComparator);
        policy.minAdjustmentStep = policy.minAdjustmentStep || 1;
        addAdjustmentAttributes(policy);
        policy.stepAdjustments.forEach(addAdjustmentAttributes);

      }
    };
  });
