import * as React from 'react';
import { angular2react } from 'angular2react';
import IInjectorService = angular.auto.IInjectorService;

import { ISubnetSelectFieldProps } from '../subnet/SubnetSelectField';
import { SubnetSelectFieldWrapperComponent } from '../subnet/subnetSelectField.component';
import { ReactInject } from '@spinnaker/core';

export class AwsNgReactInjector extends ReactInject {

  public $injectorProxy = {} as IInjectorService;

  // Reactified components
  public SubnetSelectField: React.ComponentClass<ISubnetSelectFieldProps> = angular2react('subnetSelectFieldWrapper', new SubnetSelectFieldWrapperComponent(), this.$injectorProxy) as any;

  public initialize($injector: IInjectorService) {
    const realInjector: { [key: string]: Function } = $injector as any;
    const proxyInjector: { [key: string]: Function } = this.$injectorProxy as any;

    Object.keys($injector)
      .filter(key => typeof realInjector[key] === 'function')
      .forEach(key => proxyInjector[key] = realInjector[key].bind(realInjector));
  }
}

export const AwsNgReact = new AwsNgReactInjector();
