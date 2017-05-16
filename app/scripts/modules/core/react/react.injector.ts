import * as React from 'react';
import 'ngimport';
import IInjectorService = angular.auto.IInjectorService;
import { IModalService } from 'angular-ui-bootstrap';
import { StateService, StateParams } from 'angular-ui-router';
import { IQService, IRootScopeService } from 'angular';
import { angular2react } from 'angular2react';

import { StateEvents } from './state.events';
import { Api } from '../api/api.service';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ApplicationReader } from '../application/service/application.read.service';
import { AuthenticationService } from '../authentication/authentication.service';
import { CollapsibleSectionStateCache } from '../cache/collapsibleSectionStateCache';
import { CancelModalService } from '../cancelModal/cancelModal.service';
import { CloudProviderRegistry } from '../cloudProvider/cloudProvider.registry';
import { ProviderSelectionService } from '../cloudProvider/providerSelection/providerSelection.service';
import { ClusterFilterService } from '../cluster/filter/clusterFilter.service';
import { ConfirmationModalService } from '../confirmationModal/confirmationModal.service';
import { ExecutionFilterModel } from '../delivery/filter/executionFilter.model';
import { ExecutionFilterService } from '../delivery/filter/executionFilter.service';
import { ExecutionService } from '../delivery/service/execution.service';
import { LoadBalancerFilterService } from '../loadBalancer/filter/loadBalancer.filter.service';
import { LoadBalancerFilterModel } from '../loadBalancer/filter/loadBalancerFilter.model';
import { PipelineConfigService } from '../pipeline/config/services/pipelineConfig.service';
import { PipelineConfigProvider } from '../pipeline/config/pipelineConfigProvider';
import { ManualJudgmentService } from '../pipeline/config/stages/manualJudgment/manualJudgment.service';
import { PipelineTemplateService } from '../pipeline/config/templates/pipelineTemplate.service';
import { VariableInputService } from '../pipeline/config/templates/inputs/variableInput.service';
import { VariableValidatorService } from '../pipeline/config/templates/validators/variableValidator.service';
import { SchedulerFactory } from '../scheduler/scheduler.factory';
import { InfrastructureSearchService } from '../search/infrastructure/infrastructureSearch.service';
import { WaypointService } from '../utils/waypoints/waypoint.service';
import { IHelpFieldProps } from '../help/HelpField';
import { InstancesProps } from '../instance/Instances';
import { DiffViewProps } from '../pipeline/config/actions/history/DiffView';
import { IPipelineGraphProps } from '../pipeline/config/graph/PipelineGraph';
import { ICopyToClipboardProps } from '../utils/clipboard/CopyToClipboard';
import { ISpinnerProps, SpinnerWrapperComponent } from '../widgets/Spinner';
import { AccountTagComponent } from '../account/accountTag.component';
import { IAccountTagProps } from '../account/AccountTag';
import { IExecutionDetailsProps } from '../delivery/details/ExecutionDetails';
import { ExecutionDetailsComponent } from '../delivery/details/executionDetails.component';
import { IExecutionStatusProps } from '../delivery/status/ExecutionStatus';
import { ExecutionStatusComponent } from '../delivery/status/executionStatus.component';
import { IEntityUiTagsProps } from '../entityTag/EntityUiTags';
import { EntityUiTagsWrapperComponent } from '../entityTag/entityUiTags.component';
import { IButtonBusyIndicatorProps } from '../forms/buttonBusyIndicator/ButtonBusyIndicator';
import { ButtonBusyIndicatorComponent } from '../forms/buttonBusyIndicator/buttonBusyIndicator.component';
import { HelpFieldWrapperComponent } from '../help/helpField.component';
import { InstancesWrapperComponent } from '../instance/instances.component';
import { diffViewComponent } from '../pipeline/config/actions/history/diffView.component';
import { PipelineGraphComponent } from '../pipeline/config/graph/pipeline.graph.component';
import { CopyToClipboardComponent } from '../utils/clipboard/copyToClipboard.component';

export abstract class ReactInject {
  protected $injector: IInjectorService;

  public abstract initialize($injector: IInjectorService): void;
}

export class CoreReactInject extends ReactInject {

  // weird service because we wrap it
  private wrappedState: StateService;

  public get $state() {
    if (!this.wrappedState) {
      this.wrappedState = this.createStateService();
    }
    return this.wrappedState;
  }

  // Services
  public get modalService() { return this.$injector.get('modalService') as IModalService; }
  public get $stateParams() { return this.$injector.get('$stateParams') as StateParams; }
  public get stateEvents() { return this.$injector.get('stateEvents') as StateEvents; }
  public get API() { return this.$injector.get('API') as Api; }
  public get applicationModelBuilder() { return this.$injector.get('applicationModelBuilder') as ApplicationModelBuilder; }
  public get applicationReader() { return this.$injector.get('applicationReader') as ApplicationReader; }
  public get authenticationService() { return this.$injector.get('authenticationService') as AuthenticationService; }
  public get collapsibleSectionStateCache() { return this.$injector.get('collapsibleSectionStateCache') as CollapsibleSectionStateCache; }
  public get cancelModalService() { return this.$injector.get('cancelModalService') as CancelModalService; }
  public get cloudProviderRegistry() { return this.$injector.get('cloudProviderRegistry') as CloudProviderRegistry; }
  public get providerSelectionService() { return this.$injector.get('providerSelectionService') as ProviderSelectionService; }
  public get clusterFilterService() { return this.$injector.get('clusterFilterService') as ClusterFilterService; }
  public get confirmationModalService() { return this.$injector.get('confirmationModalService') as ConfirmationModalService; }
  public get executionFilterModel() { return this.$injector.get('executionFilterModel') as ExecutionFilterModel; }
  public get executionFilterService() { return this.$injector.get('executionFilterService') as ExecutionFilterService; }
  public get executionService() { return this.$injector.get('executionService') as ExecutionService; }
  public get loadBalancerFilterService() { return this.$injector.get('loadBalancerFilterService') as LoadBalancerFilterService; }
  public get loadBalancerFilterModel() { return this.$injector.get('loadBalancerFilterModel') as LoadBalancerFilterModel; }
  public get pipelineConfig() { return this.$injector.get('pipelineConfig') as PipelineConfigProvider; }
  public get pipelineConfigService() { return this.$injector.get('pipelineConfigService') as PipelineConfigService; }
  public get manualJudgmentService() { return this.$injector.get('manualJudgmentService') as ManualJudgmentService; }
  public get pipelineTemplateService() { return this.$injector.get('pipelineTemplateService') as PipelineTemplateService; }
  public get variableInputService() { return this.$injector.get('variableInputService') as VariableInputService; }
  public get variableValidatorService() { return this.$injector.get('variableValidatorService') as VariableValidatorService; }
  public get schedulerFactory() { return this.$injector.get('schedulerFactory') as SchedulerFactory; }
  public get infrastructureSearchService() { return this.$injector.get('infrastructureSearchService') as InfrastructureSearchService; }
  public get waypointService() { return this.$injector.get('waypointService') as WaypointService; }

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

  private createStateService(): StateService {
    const wrappedState = Object.create(this.$injector.get('$state')) as StateService;
    const $rootScope = this.$injector.get('$rootScope') as IRootScopeService;
    const $q = this.$injector.get('$q') as IQService;
    const originalGo = wrappedState.go;
    wrappedState.go = function () {
      const args = arguments;
      const deferred = $q.defer();
      const promise = Object.create(deferred);
      promise.promise.transition = null;
      $rootScope.$applyAsync(() => {
        promise.transition = originalGo.apply(this, args).then((r: any) => { promise.resolve(r); });
      });
      return promise.promise;
    };
    return wrappedState;
  }

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

export const ReactInjector: CoreReactInject = new CoreReactInject();
