import { angular2react } from 'angular2react';
import React from 'react';

import { ReactInject } from '@spinnaker/core';
import { IScalingPolicySummaryProps } from '../serverGroup/details/scalingPolicy/ScalingPolicySummary';
import { scalingPolicyDetailsSummary } from '../serverGroup/details/scalingPolicy/detailsSummary.component';

import IInjectorService = angular.auto.IInjectorService;

// prettier-ignore
export class AwsNgReactInjector extends ReactInject {
  public $injectorProxy = {} as IInjectorService;

  // Reactified components
  public ScalingPolicySummary: React.ComponentClass<IScalingPolicySummaryProps> = angular2react('scalingPolicySummary', scalingPolicyDetailsSummary, this.$injectorProxy) as any;

  public initialize($injector: IInjectorService) {
    const realInjector: { [key: string]: Function } = $injector as any;
    const proxyInjector: { [key: string]: Function } = this.$injectorProxy as any;

    Object.keys($injector)
      .filter(key => typeof realInjector[key] === 'function')
      .forEach(key => proxyInjector[key] = realInjector[key].bind(realInjector));
  }
}

export const AwsNgReact = new AwsNgReactInjector();
