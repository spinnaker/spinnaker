import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

// prettier-ignore
export class TencentcloudReactInject extends ReactInject {


  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const TencentcloudReactInjector: TencentcloudReactInject = new TencentcloudReactInject();
