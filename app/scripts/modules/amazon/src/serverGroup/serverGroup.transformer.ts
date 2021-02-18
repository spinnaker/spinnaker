import { module } from 'angular';
import { defaults } from 'lodash';

import { IVpc } from '@spinnaker/core';

import {
  IAmazonServerGroup,
  IAmazonServerGroupView,
  IScalingAdjustmentView,
  IScalingPolicy,
  IScalingPolicyAlarmView,
  IScalingPolicyView,
  IStepAdjustmentView,
  ITargetTrackingPolicy,
} from '../domain';
import { VpcReader } from '../vpc/VpcReader';

export class AwsServerGroupTransformer {
  private addComparator(alarm: IScalingPolicyAlarmView): void {
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

  private addAdjustmentAttributes(policyOrStepAdjustment: IScalingAdjustmentView): void {
    policyOrStepAdjustment.operator = policyOrStepAdjustment.scalingAdjustment < 0 ? 'decrease' : 'increase';
    policyOrStepAdjustment.absAdjustment = Math.abs(policyOrStepAdjustment.scalingAdjustment);
  }

  public transformScalingPolicy(policy: IScalingPolicy): IScalingPolicyView {
    const view: IScalingPolicyView = { ...policy } as IScalingPolicyView;
    const upperBoundSorter = (a: IStepAdjustmentView, b: IStepAdjustmentView) =>
      b.metricIntervalUpperBound - a.metricIntervalUpperBound;
    const lowerBoundSorter = (a: IStepAdjustmentView, b: IStepAdjustmentView) =>
      a.metricIntervalLowerBound - b.metricIntervalLowerBound;

    view.alarms = policy.alarms || [];
    view.alarms.forEach((alarm) => this.addComparator(alarm));
    this.addAdjustmentAttributes(view); // simple policies
    if (view.stepAdjustments && view.stepAdjustments.length) {
      view.stepAdjustments.forEach((sa) => this.addAdjustmentAttributes(sa)); // step policies
      const sorter = policy.stepAdjustments.every((a) => a.metricIntervalUpperBound !== undefined)
        ? upperBoundSorter
        : lowerBoundSorter;
      view.stepAdjustments.sort((a, b) => sorter(a, b));
    }
    return view;
  }

  public normalizeServerGroupDetails(serverGroup: IAmazonServerGroup): IAmazonServerGroupView {
    const view: IAmazonServerGroupView = { ...serverGroup } as IAmazonServerGroupView;
    if (serverGroup.scalingPolicies) {
      view.scalingPolicies = serverGroup.scalingPolicies.map((policy) => this.transformScalingPolicy(policy));
    }
    return view;
  }

  public normalizeServerGroup(serverGroup: IAmazonServerGroup): PromiseLike<IAmazonServerGroup> {
    serverGroup.instances.forEach((instance) => {
      instance.vpcId = serverGroup.vpcId;
    });
    return VpcReader.listVpcs().then((vpc) => this.addVpcNameToServerGroup(serverGroup)(vpc));
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
    const command = defaults({ backingData: [], viewState: [] }, base);
    command.cloudProvider = 'aws';
    command.availabilityZones = {};
    command.availabilityZones[command.region] = base.availabilityZones;
    command.loadBalancers = (base.loadBalancers || []).concat(base.vpcLoadBalancers || []);
    command.targetGroups = base.targetGroups || [];
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

  public constructNewStepScalingPolicyTemplate(serverGroup: IAmazonServerGroup): IScalingPolicy {
    return {
      alarms: [
        {
          namespace: 'AWS/EC2',
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
      estimatedInstanceWarmup: 600,
    };
  }

  public constructNewTargetTrackingPolicyTemplate(): ITargetTrackingPolicy {
    return {
      alarms: [],
      estimatedInstanceWarmup: 300,
      targetTrackingConfiguration: {
        targetValue: null,
        predefinedMetricSpecification: {
          predefinedMetricType: 'ASGAverageCPUUtilization',
        },
      },
    };
  }
}

export const AWS_SERVER_GROUP_TRANSFORMER = 'spinnaker.amazon.serverGroup.transformer';
module(AWS_SERVER_GROUP_TRANSFORMER, []).service('awsServerGroupTransformer', AwsServerGroupTransformer);
