import { module, IPromise } from 'angular';
import { defaults } from 'lodash';

import { IVpc } from '@spinnaker/core';

import { IAmazonScalingAdjustment, IAmazonScalingPolicy, IAmazonScalingPolicyAlarm, IAmazonServerGroup, IAmazonStepAdjustment } from '../domain';
import { VPC_READ_SERVICE, VpcReader } from '../vpc/vpc.read.service';

export class AwsServerGroupTransformer {
  public constructor(private vpcReader: VpcReader) { 'ngInject'; }

  private addComparator(alarm: IAmazonScalingPolicyAlarm): void {
    if (!alarm.comparisonOperator) {
      return;
    }
    switch (alarm.comparisonOperator) {
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

  private addAdjustmentAttributes(policy: IAmazonScalingAdjustment): void {
    policy.operator = policy.scalingAdjustment < 0 ? 'decrease' : 'increase';
    policy.absAdjustment = Math.abs(policy.scalingAdjustment);
  }

  private transformScalingPolicy(policy: IAmazonScalingPolicy): void {
    const upperBoundSorter = (a: IAmazonStepAdjustment, b: IAmazonStepAdjustment) => b.metricIntervalUpperBound - a.metricIntervalUpperBound,
          lowerBoundSorter = (a: IAmazonStepAdjustment, b: IAmazonStepAdjustment) => a.metricIntervalLowerBound - b.metricIntervalLowerBound;

    policy.alarms = policy.alarms || [];
    policy.alarms.forEach((alarm) => this.addComparator(alarm));
    this.addAdjustmentAttributes(policy); // simple policies
    if (policy.stepAdjustments && policy.stepAdjustments.length) {
      policy.stepAdjustments.forEach((sa) => this.addAdjustmentAttributes(sa)); // step policies
      const sorter = policy.stepAdjustments.every(a => a.metricIntervalUpperBound !== undefined) ?
        upperBoundSorter : lowerBoundSorter;
      policy.stepAdjustments.sort((a, b) => sorter(a, b));
    }
  };

  public normalizeServerGroupDetails(serverGroup: IAmazonServerGroup): void {
    if (serverGroup.scalingPolicies) {
      serverGroup.scalingPolicies.forEach((policy) => this.transformScalingPolicy(policy));
    }
  }

  public normalizeServerGroup(serverGroup: IAmazonServerGroup): IPromise<IAmazonServerGroup> {
    serverGroup.instances.forEach((instance) => { instance.vpcId = serverGroup.vpcId; });
    return this.vpcReader.listVpcs().then((vpc) => this.addVpcNameToServerGroup(serverGroup)(vpc));
  }

  private addVpcNameToServerGroup(serverGroup: IAmazonServerGroup): (vpc: IVpc[]) => IAmazonServerGroup {
    return (vpcs: IVpc[]) => {
      const match = vpcs.find((test) => test.id === serverGroup.vpcId);
      serverGroup.vpcName = match ? match.name : '';
      return serverGroup;
    };
  }

  public convertServerGroupCommandToDeployConfiguration(base: any): any {
    // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
    const command = defaults({backingData: [], viewState: []}, base);
    command.cloudProvider = 'aws';
    command.availabilityZones = {};
    command.availabilityZones[command.region] = base.availabilityZones;
    command.loadBalancers = (base.loadBalancers || []).concat(base.vpcLoadBalancers || []);
    command.account = command.credentials;
    command.subnetType = command.subnetType || '';

    if (base.viewState.mode !== 'clone') {
      delete command.source;
    }
    if (!command.ramdiskId) {
      delete command.ramdiskId; // TODO: clean up in kato? - should ignore if empty string
    }
    delete command.region;
    delete command.viewState;
    delete command.backingData;
    delete command.selectedProvider;
    delete command.instanceProfile;
    delete command.vpcId;

    return command;
    }
}

export const AWS_SERVER_GROUP_TRANSFORMER = 'spinnaker.amazon.serverGroup.transformer';
module(AWS_SERVER_GROUP_TRANSFORMER, [
  VPC_READ_SERVICE,
])
  .service('awsServerGroupTransformer', AwsServerGroupTransformer);
