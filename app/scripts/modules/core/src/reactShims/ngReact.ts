import { angular2react } from 'angular2react';
import React from 'react';

import { IViewChangesLinkProps, ViewChangesLink as ViewChangesLinkComponent } from 'core/diffs/ViewChangesLink';
import { EntitySource as EntitySourceComponent, IEntitySourceProps } from 'core/entityTag/EntitySource';
import { INumberListProps } from 'core/forms/numberList/NumberList';
import { numberListWrapperComponent } from 'core/forms/numberList/numberList.component';
import { ITargetSelectProps, targetSelectComponent } from 'core/pipeline/config/targetSelect.component';
import { IStageSummaryWrapperProps } from 'core/pipeline/details/StageSummaryWrapper';
import { IStepExecutionDetailsWrapperProps } from 'core/pipeline/details/StepExecutionDetailsWrapper';
import { stageSummaryComponent } from 'core/pipeline/details/stageSummary.component';
import { stepExecutionDetailsComponent } from 'core/pipeline/details/stepExecutionDetails.component';
import { IInstanceArchetypeSelectorProps } from 'core/serverGroup/configure/common/InstanceArchetypeSelector';
import { IInstanceTypeSelectorProps } from 'core/serverGroup/configure/common/InstanceTypeSelector';
import { v2InstanceTypeSelector } from 'core/serverGroup/configure/common/v2InstanceTypeSelector.component';
import { v2InstanceArchetypeSelector } from 'core/serverGroup/configure/common/v2instanceArchetypeSelector.component';

import { ITaskMonitorProps } from 'core/task/monitor/TaskMonitorWrapper';
import { TaskMonitorWrapper } from 'core/task/monitor/TaskMonitorWrapper';
import { IAccountRegionClusterSelectorProps } from 'core/widgets/AccountRegionClusterSelector';
import { accountRegionClusterSelectorWrapperComponent } from 'core/widgets/accountRegionClusterSelectorWrapper.component';

import { ReactInject } from './react.injector';

import IInjectorService = angular.auto.IInjectorService;

// prettier-ignore
export class NgReactInjector extends ReactInject {
  public $injectorProxy = {} as IInjectorService;

  // Reactified components
  public AccountRegionClusterSelector: React.ComponentClass<IAccountRegionClusterSelectorProps> = angular2react('accountRegionClusterSelectorWrapper', accountRegionClusterSelectorWrapperComponent, this.$injectorProxy) as any;
  public EntitySource: React.FunctionComponent<IEntitySourceProps>                                 = EntitySourceComponent;
  public InstanceArchetypeSelector: React.ComponentClass<IInstanceArchetypeSelectorProps>       = angular2react('v2InstanceArchetypeSelector', v2InstanceArchetypeSelector, this.$injectorProxy) as any;
  public InstanceTypeSelector: React.ComponentClass<IInstanceTypeSelectorProps>                 = angular2react('v2InstanceTypeSelector', v2InstanceTypeSelector, this.$injectorProxy);
  public NumberList: React.ComponentClass<INumberListProps>                                     = angular2react('numberListWrapper', numberListWrapperComponent, this.$injectorProxy) as any;
  public StageSummaryWrapper: React.ComponentClass<IStageSummaryWrapperProps>                   = angular2react('stageSummary', stageSummaryComponent, this.$injectorProxy) as any;
  public StepExecutionDetailsWrapper: React.ComponentClass<IStepExecutionDetailsWrapperProps>   = angular2react('stepExecutionDetails', stepExecutionDetailsComponent, this.$injectorProxy) as any;
  public TargetSelect: React.ComponentClass<ITargetSelectProps>                                 = angular2react('targetSelect', targetSelectComponent, this.$injectorProxy) as any;
  public TaskMonitorWrapper: React.FunctionComponent<ITaskMonitorProps>                         = TaskMonitorWrapper;
  public ViewChangesLink: React.FunctionComponent<IViewChangesLinkProps>                           = ViewChangesLinkComponent;

  public initialize($injector: IInjectorService) {
    const realInjector: { [key: string]: Function } = $injector as any;
    const proxyInjector: { [key: string]: Function } = this.$injectorProxy as any;

    Object.keys($injector)
      .filter(key => typeof realInjector[key] === 'function')
      .forEach(key => (proxyInjector[key] = realInjector[key].bind(realInjector)));
  }
}

export const NgReact = new NgReactInjector();
