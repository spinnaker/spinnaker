import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

// prettier-ignore
export class TencentcloudNgReactInjector extends ReactInject {
  public $injectorProxy = {} as IInjectorService;

  // Reactified components

  public initialize($injector: IInjectorService) {
    const realInjector: { [key: string]: Function } = $injector as any;
    const proxyInjector: { [key: string]: Function } = this.$injectorProxy as any;

    Object.keys($injector)
      .filter(key => typeof realInjector[key] === 'function')
      .forEach(key => proxyInjector[key] = realInjector[key].bind(realInjector));
  }
}

export const TencentcloudNgReact = new TencentcloudNgReactInjector();
