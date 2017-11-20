import * as React from 'react';
import { angular2react } from 'angular2react';
import IInjectorService = angular.auto.IInjectorService;
import { AccountTagComponent } from '../account/accountTag.component';
import { ApplicationNavComponent } from 'core/application/nav/applicationNav.component';
import { ApplicationNavSecondaryComponent } from 'core/application/nav/applicationNavSecondary.component';
import { ApplicationsComponent } from 'core/application/applications.component';
import { ButtonBusyIndicatorComponent } from '../forms/buttonBusyIndicator/buttonBusyIndicator.component';
import { CopyToClipboardComponent } from '../utils/clipboard/copyToClipboard.component';
import { DiffViewProps } from '../pipeline/config/actions/history/DiffView';
import { HelpFieldWrapperComponent } from '../help/helpField.component';
import { IAccountTagProps } from '../account/AccountTag';
import { IApplicationNavProps } from 'core/application/nav/ApplicationNav';
import { IApplicationNavSecondaryProps } from 'core/application/nav/ApplicationNavSecondary';
import { IButtonBusyIndicatorProps } from '../forms/buttonBusyIndicator/ButtonBusyIndicator';
import { ICopyToClipboardProps } from '../utils/clipboard/CopyToClipboard';
import { IHelpFieldProps } from '../help/HelpField';
import { IInsightLayoutProps } from 'core/insight/InsightLayout';
import { IInstanceListProps, instanceListBindings } from '../instance/InstanceList';
import { InsightLayoutComponent } from 'core/insight/insightLayout.component';
import { IRunningTasksTagProps, runningTasksTagBindings } from '../serverGroup/pod/RunningTasksTag';
import { ILegacySpinnerProps, SpinnerWrapperComponent } from '../widgets/Spinner';
import { ITaskMonitorProps } from 'core/task/monitor/TaskMonitor';
import { ReactInject } from './react.injector';
import { TaskMonitorWrapperComponent } from 'core/task/monitor/taskMonitor.directive';
import { diffViewComponent } from '../pipeline/config/actions/history/diffView.component';
import { IStageSummaryWrapperProps } from 'core/pipeline/details/StageSummaryWrapper';
import { IStageDetailsWrapperProps } from 'core/pipeline/details/StageDetailsWrapper';
import { StageSummaryComponent } from 'core/pipeline/details/stageSummary.component';
import { StageDetailsComponent } from 'core/pipeline/details/stageDetails.component';

export class NgReactInjector extends ReactInject {

  public $injectorProxy = {} as IInjectorService;

  // Reactified components
  public AccountTag: React.ComponentClass<IAccountTagProps>                           = angular2react('accountTag', new AccountTagComponent(), this.$injectorProxy) as any;
  public ApplicationNav: React.ComponentClass<IApplicationNavProps>                   = angular2react('applicationNav', new ApplicationNavComponent(), this.$injectorProxy) as any;
  public ApplicationNavSecondary: React.ComponentClass<IApplicationNavSecondaryProps> = angular2react('applicationNavSecondary', new ApplicationNavSecondaryComponent(), this.$injectorProxy) as any;
  public Applications: React.ComponentClass<{}>                                       = angular2react('applications', new ApplicationsComponent(), this.$injectorProxy) as any;
  public ButtonBusyIndicator: React.ComponentClass<IButtonBusyIndicatorProps>         = angular2react('buttonBusyIndicator', new ButtonBusyIndicatorComponent(), this.$injectorProxy) as any;
  public CopyToClipboard: React.ComponentClass<ICopyToClipboardProps>                 = angular2react('copyToClipboard', new CopyToClipboardComponent(), this.$injectorProxy) as any;
  public DiffView: React.ComponentClass<DiffViewProps>                                = angular2react('diffView', diffViewComponent, this.$injectorProxy) as any;
  public HelpField: React.ComponentClass<IHelpFieldProps>                             = angular2react('helpFieldWrapper', new HelpFieldWrapperComponent(), this.$injectorProxy) as any;
  public InsightLayout: React.ComponentClass<IInsightLayoutProps>                     = angular2react('insightLayout', new InsightLayoutComponent(), this.$injectorProxy) as any;
  public InstanceList: React.ComponentClass<IInstanceListProps>                       = angular2react('instanceList', { bindings: instanceListBindings }, this.$injectorProxy) as any;
  public RunningTasksTag: React.ComponentClass<IRunningTasksTagProps>                 = angular2react('runningTasksTag', { bindings: runningTasksTagBindings }, this.$injectorProxy) as any;
  public LegacySpinner: React.ComponentClass<ILegacySpinnerProps>                     = angular2react('spinnerWrapper', new SpinnerWrapperComponent(), this.$injectorProxy) as any;
  public TaskMonitorWrapper: React.ComponentClass<ITaskMonitorProps>                  = angular2react('taskMonitorWrapper', new TaskMonitorWrapperComponent(), this.$injectorProxy) as any;
  public UserMenu: React.ComponentClass<{}>                                           = angular2react('userMenu', {}, this.$injectorProxy) as any;
  public GlobalSearch: React.ComponentClass<{}>                                       = angular2react('globalSearch', {}, this.$injectorProxy) as any;
  public WhatsNew: React.ComponentClass<{}>                                           = angular2react('whatsNew', {}, this.$injectorProxy) as any;
  public StageSummaryWrapper: React.ComponentClass<IStageSummaryWrapperProps>         = angular2react('stageSummary', new StageSummaryComponent(), this.$injectorProxy) as any;
  public StageDetailsWrapper: React.ComponentClass<IStageDetailsWrapperProps>         = angular2react('stageDetails', new StageDetailsComponent(), this.$injectorProxy) as any;

  public initialize($injector: IInjectorService) {
    const realInjector: { [key: string]: Function } = $injector as any;
    const proxyInjector: { [key: string]: Function } = this.$injectorProxy as any;

    Object.keys($injector)
      .filter(key => typeof realInjector[key] === 'function')
      .forEach(key => proxyInjector[key] = realInjector[key].bind(realInjector));
  }
}

export const NgReact = new NgReactInjector();
