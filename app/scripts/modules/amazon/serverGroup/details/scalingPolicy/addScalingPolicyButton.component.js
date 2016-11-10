'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.details.scalingPolicy.addButton', [
    require('angular-ui-bootstrap'),
  ])
  .component('addScalingPolicyButton', {
    bindings: {
      serverGroup: '=',
      application: '=',
    },
    template: '<a href ng-click="$ctrl.addScalingPolicy()">Create new scaling policy</a>',
    controller: function($uibModal) {

      let policy = {
        alarms: [{
          statistic: 'Average',
          comparisonOperator: 'GreaterThanThreshold',
          evaluationPeriods: 1,
          dimensions: [{ name: 'AutoScalingGroupName', value: this.serverGroup.name}],
          period: 60,
        }],
        adjustmentType: 'ChangeInCapacity',
        stepAdjustments: [{
          scalingAdjustment: 1,
        }],
        estimatedInstanceWarmup: 600,
      };

      this.addScalingPolicy = () => {
        $uibModal.open({
          templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
          controller: 'awsUpsertScalingPolicyCtrl',
          controllerAs: 'ctrl',
          size: 'lg',
          resolve: {
            policy: () => policy,
            serverGroup: () => this.serverGroup,
            application: () => this.application,
          }
        });
      };
    }
  });
