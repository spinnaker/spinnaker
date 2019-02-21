'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.titus.serverGroup.transformer', [])
  .factory('titusServerGroupTransformer', ['$q', function($q) {
    function normalizeServerGroup(serverGroup) {
      return $q.when(serverGroup); // no-op
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({ backingData: [], viewState: [] }, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }
      command.account = command.credentials;

      if (!command.efs.mountPoint || !command.efs.efsId || !command.efs.mountPerm) {
        delete command.efs;
      }

      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;
      return command;
    }

    function constructNewStepScalingPolicyTemplate(serverGroup) {
      return {
        alarms: [
          {
            namespace: 'NFLX/EPIC',
            metricName: 'CPUUtilization',
            threshold: 50,
            statistic: 'Average',
            comparisonOperator: 'GreaterThanThreshold',
            evaluationPeriods: 1,
            dimensions: [{ name: 'AutoScalingGroupName', value: serverGroup.name }],
            period: 60,
          },
        ],
        adjustmentType: 'ChangeInCapacity',
        stepAdjustments: [
          {
            scalingAdjustment: 1,
            metricIntervalLowerBound: 0,
          },
        ],
        cooldown: 300,
      };
    }

    function constructNewTargetTrackingPolicyTemplate(serverGroup) {
      return {
        alarms: [],
        targetTrackingConfiguration: {
          targetValue: null,
          disableScaleIn: false,
          customizedMetricSpecification: {
            namespace: 'NFLX/EPIC',
            metricName: 'CPUUtilization',
            dimensions: [{ name: 'AutoScalingGroupName', value: serverGroup.name }],
          },
          scaleInCooldown: 300,
          scaleOutCooldown: 300,
        },
      };
    }

    return {
      convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup,
      constructNewStepScalingPolicyTemplate,
      constructNewTargetTrackingPolicyTemplate,
    };
  }]);
