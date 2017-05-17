import IInjectorService = angular.auto.IInjectorService;
import { IModalService } from 'angular-ui-bootstrap';
import { StateService, StateParams } from 'angular-ui-router';
import { IQService, IRootScopeService } from 'angular';

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
  public get modalService() { return this.$injector.get('$uibModal') as IModalService; }
  public get pipelineConfig() { return this.$injector.get('pipelineConfig') as PipelineConfigProvider; }
  public get pipelineConfigService() { return this.$injector.get('pipelineConfigService') as PipelineConfigService; }
  public get manualJudgmentService() { return this.$injector.get('manualJudgmentService') as ManualJudgmentService; }
  public get pipelineTemplateService() { return this.$injector.get('pipelineTemplateService') as PipelineTemplateService; }
  public get variableInputService() { return this.$injector.get('variableInputService') as VariableInputService; }
  public get variableValidatorService() { return this.$injector.get('variableValidatorService') as VariableValidatorService; }
  public get schedulerFactory() { return this.$injector.get('schedulerFactory') as SchedulerFactory; }
  public get infrastructureSearchService() { return this.$injector.get('infrastructureSearchService') as InfrastructureSearchService; }
  public get waypointService() { return this.$injector.get('waypointService') as WaypointService; }

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
  }
}

export const ReactInjector: CoreReactInject = new CoreReactInject();
