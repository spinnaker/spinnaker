'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.serverGroup.transformer', [
    require('../../core/utils/lodash.js'),
    require('../vpc/vpc.read.service.js'),
  ])
  .factory('awsServerGroupTransformer', function (_, vpcReader) {

    function addComparator(alarm) {
      if (!alarm.comparisonOperator) {
        return;
      }
      switch(alarm.comparisonOperator) {
        case 'LessThanThreshold':
          alarm.comparator = '&lt;';
          break;
        case 'GreaterThanThreshold':
          alarm.comparator = '&gt;';
          break;
        case 'LessThanOrEqualToThreshold':
          alarm.comparator = '&le;';
          break;
        case 'GreaterThanOrEqualToThreshold':
          alarm.comparator = '&ge;';
          break;
      }
    }

    function addAdjustmentAttributes(adjuster) {
      adjuster.operator = adjuster.scalingAdjustment < 0 ? 'decrease' : 'increase';
      adjuster.absAdjustment = Math.abs(adjuster.scalingAdjustment);
    }

    let upperBoundSorter = (a, b) => b.metricIntervalUpperBound - a.metricIntervalUpperBound,
        lowerBoundSorter = (a, b) => a.metricIntervalLowerBound - b.metricIntervalLowerBound;

    let transformScalingPolicy = (policy) => {
      policy.alarms = policy.alarms || [];
      policy.alarms.forEach(addComparator);
      addAdjustmentAttributes(policy); // simple policies
      if (policy.stepAdjustments && policy.stepAdjustments.length) {
        policy.stepAdjustments.forEach(addAdjustmentAttributes); // step policies
        let sorter = policy.stepAdjustments.every(a => a.metricIntervalUpperBound !== undefined) ?
          upperBoundSorter : lowerBoundSorter;
        policy.stepAdjustments.sort(sorter);
      }
    };

    function normalizeServerGroupDetails(serverGroup) {
      if (serverGroup.scalingPolicies) {
        serverGroup.scalingPolicies.forEach(transformScalingPolicy);
      }
    }

    function normalizeServerGroup(serverGroup) {
      serverGroup.instances.forEach((instance) => { instance.vpcId = serverGroup.vpcId; });
      return vpcReader.listVpcs().then(addVpcNameToServerGroup(serverGroup));
    }

    function addVpcNameToServerGroup(serverGroup) {
      return function(vpcs) {
        var matches = vpcs.filter(function(test) {
          return test.id === serverGroup.vpcId;
        });
        serverGroup.vpcName = matches.length ? matches[0].name : '';
        return serverGroup;
      };
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({backingData: [], viewState: []}, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }
      command.cloudProvider = 'aws';
      command.availabilityZones = {};
      command.availabilityZones[command.region] = base.availabilityZones;
      command.loadBalancers = (base.loadBalancers || []).concat(base.vpcLoadBalancers || []);
      command.account = command.credentials;
      if (!command.ramdiskId) {
        delete command.ramdiskId; // TODO: clean up in kato? - should ignore if empty string
      }
      delete command.region;
      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;
      delete command.instanceProfile;
      delete command.vpcId;

      if (!command.subnetType) {
        command.subnetType = '';
      }
      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
      normalizeServerGroupDetails: normalizeServerGroupDetails,
    };

  });
