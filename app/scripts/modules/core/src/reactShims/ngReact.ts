import * as React from 'react';
import { angular2react } from 'angular2react';
import IInjectorService = angular.auto.IInjectorService;
import { ReactInject } from './react.injector';
import { ISpinnerProps, SpinnerWrapperComponent } from '../widgets/Spinner';
import { IPipelineGraphProps } from '../pipeline/config/graph/PipelineGraph';
import { InstancesProps } from '../instance/Instances';
import { IHelpFieldProps } from '../help/HelpField';
import { IExecutionStatusProps } from '../delivery/status/ExecutionStatus';
import { IExecutionDetailsProps } from '../delivery/details/ExecutionDetails';
import { IEntityUiTagsProps } from '../entityTag/EntityUiTags';
import { DiffViewProps } from '../pipeline/config/actions/history/DiffView';
import { ICopyToClipboardProps } from '../utils/clipboard/CopyToClipboard';
import { IAccountTagProps } from '../account/AccountTag';
import { IButtonBusyIndicatorProps } from '../forms/buttonBusyIndicator/ButtonBusyIndicator';
import { AccountTagComponent } from '../account/accountTag.component';
import { ButtonBusyIndicatorComponent } from '../forms/buttonBusyIndicator/buttonBusyIndicator.component';
import { CopyToClipboardComponent } from '../utils/clipboard/copyToClipboard.component';
import { diffViewComponent } from '../pipeline/config/actions/history/diffView.component';
import { EntityUiTagsWrapperComponent } from '../entityTag/entityUiTags.component';
import { ExecutionDetailsComponent } from '../delivery/details/executionDetails.component';
import { ExecutionStatusComponent } from '../delivery/status/executionStatus.component';
import { HelpFieldWrapperComponent } from '../help/helpField.component';
import { InstancesWrapperComponent } from '../instance/instances.component';
import { PipelineGraphComponent } from '../pipeline/config/graph/pipeline.graph.component';

export class NgReactInjector extends ReactInject {

  // Reactified components
  public AccountTag: React.ComponentClass<IAccountTagProps>;
  public ButtonBusyIndicator: React.ComponentClass<IButtonBusyIndicatorProps>;
  public CopyToClipboard: React.ComponentClass<ICopyToClipboardProps>;
  public DiffView: React.ComponentClass<DiffViewProps>;
  public EntityUiTags: React.ComponentClass<IEntityUiTagsProps>;
  public ExecutionDetails: React.ComponentClass<IExecutionDetailsProps>;
  public ExecutionStatus: React.ComponentClass<IExecutionStatusProps>;
  public HelpField: React.ComponentClass<IHelpFieldProps>;
  public Instances: React.ComponentClass<InstancesProps>;
  public PipelineGraph: React.ComponentClass<IPipelineGraphProps>;
  public Spinner: React.ComponentClass<ISpinnerProps>;

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
    this.AccountTag = angular2react('accountTag', new AccountTagComponent(), $injector) as any;
    this.ButtonBusyIndicator = angular2react('buttonBusyIndicator', new ButtonBusyIndicatorComponent(), $injector) as any;
    this.CopyToClipboard = angular2react('copyToClipboard', new CopyToClipboardComponent(), $injector) as any;
    this.DiffView = angular2react('diffView', diffViewComponent, $injector) as any;
    this.EntityUiTags = angular2react('entityUiTagsWrapper', new EntityUiTagsWrapperComponent(), $injector) as any;
    this.ExecutionDetails = angular2react('executionDetails', new ExecutionDetailsComponent(), $injector) as any;
    this.ExecutionStatus = angular2react('executionStatus', new ExecutionStatusComponent(), $injector) as any;
    this.HelpField = angular2react('helpFieldWrapper', new HelpFieldWrapperComponent(), $injector) as any;
    this.Instances = angular2react('instancesWrapper', new InstancesWrapperComponent(), $injector) as any;
    this.PipelineGraph = angular2react('pipelineGraph', new PipelineGraphComponent(), $injector) as any;
    this.Spinner = angular2react('spinnerWrapper', new SpinnerWrapperComponent(), $injector) as any;
  }
}

export const NgReact = new NgReactInjector();
