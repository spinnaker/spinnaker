import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

export class TitusReactInject extends ReactInject {

  public get titusServerGroupTransformer() { return this.$injector.get('titusServerGroupTransformer') as any; }

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }

}

export const TitusReactInjector: TitusReactInject = new TitusReactInject();
