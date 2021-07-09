import { angular2react } from 'angular2react';
import React from 'react';

import { ITargetSelectProps, targetSelectComponent } from '../pipeline/config/targetSelect.component';
import { IStageSummaryWrapperProps } from '../pipeline/details/StageSummaryWrapper';
import { IStepExecutionDetailsWrapperProps } from '../pipeline/details/StepExecutionDetailsWrapper';
import { stageSummaryComponent } from '../pipeline/details/stageSummary.component';
import { stepExecutionDetailsComponent } from '../pipeline/details/stepExecutionDetails.component';
import { ReactInject } from './react.injector';
import { IInstanceArchetypeSelectorProps } from '../serverGroup/configure/common/InstanceArchetypeSelector';
import { IInstanceTypeSelectorProps } from '../serverGroup/configure/common/InstanceTypeSelector';
import { v2InstanceTypeSelector } from '../serverGroup/configure/common/v2InstanceTypeSelector.component';
import { v2InstanceArchetypeSelector } from '../serverGroup/configure/common/v2instanceArchetypeSelector.component';
import { IAccountRegionClusterSelectorProps } from '../widgets/AccountRegionClusterSelector';
import { accountRegionClusterSelectorWrapperComponent } from '../widgets/accountRegionClusterSelectorWrapper.component';

import IInjectorService = angular.auto.IInjectorService;

// prettier-ignore
export class NgReactInjector extends ReactInject {
  public $injectorProxy = {} as IInjectorService;

  // Reactified components
  public AccountRegionClusterSelector: React.ComponentClass<IAccountRegionClusterSelectorProps> = angular2react('accountRegionClusterSelectorWrapper', accountRegionClusterSelectorWrapperComponent, this.$injectorProxy) as any;
  public InstanceArchetypeSelector: React.ComponentClass<IInstanceArchetypeSelectorProps>       = angular2react('v2InstanceArchetypeSelector', v2InstanceArchetypeSelector, this.$injectorProxy) as any;
  public InstanceTypeSelector: React.ComponentClass<IInstanceTypeSelectorProps>                 = angular2react('v2InstanceTypeSelector', v2InstanceTypeSelector, this.$injectorProxy);
  public StageSummaryWrapper: React.ComponentClass<IStageSummaryWrapperProps>                   = angular2react('stageSummary', stageSummaryComponent, this.$injectorProxy) as any;
  public StepExecutionDetailsWrapper: React.ComponentClass<IStepExecutionDetailsWrapperProps>   = angular2react('stepExecutionDetails', stepExecutionDetailsComponent, this.$injectorProxy) as any;
  public TargetSelect: React.ComponentClass<ITargetSelectProps>                                 = angular2react('targetSelect', targetSelectComponent, this.$injectorProxy) as any;

  public initialize($injector: IInjectorService) {
    const realInjector: { [key: string]: Function } = $injector as any;
    const proxyInjector: { [key: string]: Function } = this.$injectorProxy as any;

    Object.keys($injector)
      .filter(key => typeof realInjector[key] === 'function')
      .forEach(key => (proxyInjector[key] = realInjector[key].bind(realInjector)));
  }
}

export const NgReact = new NgReactInjector();
