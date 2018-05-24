import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

import { AwsServerGroupTransformer } from '../serverGroup/serverGroup.transformer';
import { AwsLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';

// prettier-ignore
export class AwsReactInject extends ReactInject {
  public get autoScalingProcessService() { return this.$injector.get('autoScalingProcessService') as any; }
  public get awsLoadBalancerTransformer() { return this.$injector.get('awsLoadBalancerTransformer') as AwsLoadBalancerTransformer; }
  public get awsServerGroupCommandBuilder() { return this.$injector.get('awsServerGroupCommandBuilder') as any; }
  public get awsServerGroupTransformer() { return this.$injector.get('awsServerGroupTransformer') as AwsServerGroupTransformer; }

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const AwsReactInjector: AwsReactInject = new AwsReactInject();
