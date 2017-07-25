import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

import { AwsServerGroupTransformer } from '../serverGroup/serverGroup.transformer';

export class AwsReactInject extends ReactInject {

  public get awsServerGroupTransformer() { return this.$injector.get('awsServerGroupTransformer') as AwsServerGroupTransformer; }

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }

}

export const AwsReactInjector: AwsReactInject = new AwsReactInject();
