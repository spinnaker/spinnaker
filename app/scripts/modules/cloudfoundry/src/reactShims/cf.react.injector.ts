import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

import { CloudFoundryLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';

// prettier-ignore
export class CloudFoundryReactInject extends ReactInject {
  public get cfLoadBalancerTransformer() { return this.$injector.get('cfLoadBalancerTransformer') as CloudFoundryLoadBalancerTransformer; }

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const CloudFoundryReactInjector: CloudFoundryReactInject = new CloudFoundryReactInject();
