import { module, IPromise } from 'angular';
import { defaults } from 'lodash';

import { IVpc } from '@spinnaker/core';

import {
  IScalingAdjustmentView,
  IScalingPolicyView,
  IScalingPolicyAlarmView,
  IAmazonServerGroup,
  IStepAdjustmentView,
  IScalingPolicy,
  IAmazonServerGroupView,
} from '@spinnaker/amazon';
import { VPC_READ_SERVICE, VpcReader } from '@spinnaker/amazon';

export class EcsServerGroupTransformer {
  public constructor(private vpcReader: VpcReader) {
    'ngInject';
  }

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

  private transformScalingPolicy(policy: IScalingPolicy): IScalingPolicyView {
    const view: IScalingPolicyView = Object.assign({}, policy) as IScalingPolicyView;
    const upperBoundSorter = (a: IStepAdjustmentView, b: IStepAdjustmentView) =>
        b.metricIntervalUpperBound - a.metricIntervalUpperBound,
      lowerBoundSorter = (a: IStepAdjustmentView, b: IStepAdjustmentView) =>
        a.metricIntervalLowerBound - b.metricIntervalLowerBound;

    view.alarms = policy.alarms || [];
    view.alarms.forEach(alarm => this.addComparator(alarm));
    this.addAdjustmentAttributes(view); // simple policies
    if (view.stepAdjustments && view.stepAdjustments.length) {
      view.stepAdjustments.forEach(sa => this.addAdjustmentAttributes(sa)); // step policies
      const sorter = policy.stepAdjustments.every(a => a.metricIntervalUpperBound !== undefined)
        ? upperBoundSorter
        : lowerBoundSorter;
      view.stepAdjustments.sort((a, b) => sorter(a, b));
    }
    return view;
  }

  public normalizeServerGroupDetails(serverGroup: IAmazonServerGroup): IAmazonServerGroupView {
    const view: IAmazonServerGroupView = Object.assign({}, serverGroup) as IAmazonServerGroupView;
    if (serverGroup.scalingPolicies) {
      view.scalingPolicies = serverGroup.scalingPolicies.map(policy => this.transformScalingPolicy(policy));
    }
    return view;
  }

  public normalizeServerGroup(serverGroup: IAmazonServerGroup): IPromise<IAmazonServerGroup> {
    serverGroup.instances.forEach(instance => {
      instance.vpcId = serverGroup.vpcId;
    });
    return this.vpcReader.listVpcs().then(vpc => this.addVpcNameToServerGroup(serverGroup)(vpc));
  }

  private addVpcNameToServerGroup(serverGroup: IAmazonServerGroup): (vpc: IVpc[]) => IAmazonServerGroup {
    return (vpcs: IVpc[]) => {
      const match = vpcs.find(test => test.id === serverGroup.vpcId);
      serverGroup.vpcName = match ? match.name : '';
      return serverGroup;
    };
  }

  public convertServerGroupCommandToDeployConfiguration(base: any): any {
    // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
    const command = defaults({ backingData: [], viewState: [] }, base);
    command.cloudProvider = 'ecs';
    command.availabilityZones = {};
    command.availabilityZones[command.region] = base.availabilityZones;
    command.loadBalancers = (base.loadBalancers || []).concat(base.vpcLoadBalancers || []);
    command.targetGroup = base.targetGroup || '';
    command.account = command.credentials;
    command.subnetType = command.subnetType || '';

    if (base.viewState.mode !== 'clone') {
      delete command.source;
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

export const ECS_SERVER_GROUP_TRANSFORMER = 'spinnaker.ecs.serverGroup.transformer';
module(ECS_SERVER_GROUP_TRANSFORMER, [VPC_READ_SERVICE]).service(
  'ecsServerGroupTransformer',
  EcsServerGroupTransformer,
);
