import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

// prettier-ignore
export class TitusReactInject extends ReactInject {
  public get titusServerGroupTransformer() { return this.$injector.get('titusServerGroupTransformer') as any; }
  public get titusServerGroupCommandBuilder() { return this.$injector.get('titusServerGroupCommandBuilder') as any; }
  public get titusServerGroupConfigurationService() { return this.$injector.get('titusServerGroupConfigurationService') as any; }
  public get titusSecurityGroupReader() { return this.$injector.get('titusSecurityGroupReader') as any; }

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const TitusReactInjector: TitusReactInject = new TitusReactInject();
