import { IQService, IRootScopeService, IScope } from 'angular';
import IInjectorService = angular.auto.IInjectorService;

import { IModalService } from 'angular-ui-bootstrap';
import { StateParams, StateService, UIRouter } from '@uirouter/core';

import { AccountService } from '../account/account.service';
import { Api } from '../api/api.service';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ApplicationReader } from '../application/service/application.read.service';
import { AuthenticationService } from '../authentication/authentication.service';
import { CacheInitializerService } from 'core/cache';
import { CancelModalService } from '../cancelModal/cancelModal.service';
import { CloudProviderRegistry } from '../cloudProvider/cloudProvider.registry';
import { ClusterFilterModel } from 'core/cluster/filter/clusterFilter.model';
import { ClusterFilterService } from '../cluster/filter/clusterFilter.service';
import { CollapsibleSectionStateCache } from '../cache/collapsibleSectionStateCache';
import { ConfirmationModalService } from '../confirmationModal/confirmationModal.service';
import { EntityTagWriter } from '../entityTag';
import { ExecutionDetailsSectionService } from 'core/pipeline/details/executionDetailsSection.service';
import { ExecutionFilterModel } from '../pipeline/filter/executionFilter.model';
import { ExecutionFilterService } from '../pipeline/filter/executionFilter.service';
import { ExecutionService } from '../pipeline/service/execution.service';
import { ExecutionsTransformerService } from '../pipeline/service/executions.transformer.service';
import { HelpContentsRegistry, IHelpContents } from 'core/help';
import { InfrastructureSearchService } from '../search/infrastructure/infrastructureSearch.service';
import { InfrastructureSearchServiceV2 } from 'core/search/infrastructure/infrastructureSearchV2.service';
import { InsightFilterStateModel } from '../insight/insightFilterState.model';
import { LoadBalancerFilterModel } from '../loadBalancer/filter/loadBalancerFilter.model';
import { LoadBalancerFilterService } from '../loadBalancer/filter/loadBalancer.filter.service';
import { ManualJudgmentService } from '../pipeline/config/stages/manualJudgment/manualJudgment.service';
import { NamingService } from '../naming/naming.service';
import { NotifierService } from '../widgets/notifier/notifier.service';
import { OverrideRegistry } from '../overrideRegistry/override.registry';
import { PageTitleService } from 'core/pageTitle';
import { PagerDutyReader } from '../pagerDuty/pagerDuty.read.service';
import { PagerDutyWriter } from '../pagerDuty/pagerDuty.write.service';
import { PipelineConfigProvider } from '../pipeline/config/pipelineConfigProvider';
import { PipelineConfigService } from '../pipeline/config/services/pipelineConfig.service';
import { PipelineConfigValidator } from '../pipeline/config/validation/pipelineConfig.validator';
import { PipelineTemplateService } from '../pipeline/config/templates/pipelineTemplate.service';
import { ProviderSelectionService } from '../cloudProvider/providerSelection/providerSelection.service';
import { RecentHistoryService } from 'core/history/recentHistory.service'
import { SchedulerFactory } from '../scheduler/scheduler.factory';
import { ScrollToService } from '../utils/scrollTo/scrollTo.service';
import { StateEvents } from './state.events';
import { TaskExecutor } from '../task/taskExecutor';
import { TaskMonitorBuilder } from '../task/monitor/taskMonitor.builder';
import { TaskReader } from '../task/task.read.service';
import { UrlBuilderService } from 'core/navigation/urlBuilder.service';
import { VariableInputService } from '../pipeline/config/templates/inputs/variableInput.service';
import { VariableValidatorService } from '../pipeline/config/templates/validators/variableValidator.service';
import { VersionSelectionService } from '../cloudProvider/versionSelection/versionSelection.service';
import { ViewStateCacheService } from '../cache/viewStateCache.service';
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
  public get $q() { return this.$injector.get('$q') as IQService; }
  public get $rootScope() { return this.$injector.get('$rootScope') as IScope; }
  public get $stateParams() { return this.$injector.get('$stateParams') as StateParams; }
  public get $uiRouter() { return this.$injector.get('$uiRouter') as UIRouter; }
  public get API() { return this.$injector.get('API') as Api; }
  public get accountService() { return this.$injector.get('accountService') as AccountService; }
  public get applicationModelBuilder() { return this.$injector.get('applicationModelBuilder') as ApplicationModelBuilder; }
  public get applicationReader() { return this.$injector.get('applicationReader') as ApplicationReader; }
  public get authenticationService() { return this.$injector.get('authenticationService') as AuthenticationService; }
  public get cacheInitializer() { return this.$injector.get('cacheInitializer') as CacheInitializerService; }
  public get cancelModalService() { return this.$injector.get('cancelModalService') as CancelModalService; }
  public get cloudProviderRegistry() { return this.$injector.get('cloudProviderRegistry') as CloudProviderRegistry; }
  public get clusterFilterModel() { return this.$injector.get('clusterFilterModel') as ClusterFilterModel; }
  public get clusterFilterService() { return this.$injector.get('clusterFilterService') as ClusterFilterService; }
  public get collapsibleSectionStateCache() { return this.$injector.get('collapsibleSectionStateCache') as CollapsibleSectionStateCache; }
  public get confirmationModalService() { return this.$injector.get('confirmationModalService') as ConfirmationModalService; }
  public get entityTagWriter() { return this.$injector.get('entityTagWriter') as EntityTagWriter; }
  public get executionDetailsSectionService() { return this.$injector.get('executionDetailsSectionService') as ExecutionDetailsSectionService; }
  public get executionFilterModel() { return this.$injector.get('executionFilterModel') as ExecutionFilterModel; }
  public get executionFilterService() { return this.$injector.get('executionFilterService') as ExecutionFilterService; }
  public get executionService() { return this.$injector.get('executionService') as ExecutionService; }
  public get executionsTransformer() { return this.$injector.get('executionsTransformer') as ExecutionsTransformerService; }
  public get helpContents() { return this.$injector.get('helpContents') as IHelpContents }
  public get helpContentsRegistry() { return this.$injector.get('helpContentsRegistry') as HelpContentsRegistry; }
  public get infrastructureSearchService() { return this.$injector.get('infrastructureSearchService') as InfrastructureSearchService; }
  public get infrastructureSearchServiceV2() { return this.$injector.get('infrastructureSearchServiceV2') as InfrastructureSearchServiceV2; }
  public get insightFilterStateModel() { return this.$injector.get('insightFilterStateModel') as InsightFilterStateModel; }
  public get loadBalancerFilterModel() { return this.$injector.get('loadBalancerFilterModel') as LoadBalancerFilterModel; }
  public get loadBalancerFilterService() { return this.$injector.get('loadBalancerFilterService') as LoadBalancerFilterService; }
  public get manualJudgmentService() { return this.$injector.get('manualJudgmentService') as ManualJudgmentService; }
  public get modalService(): IModalService { return this.$injector.get('$uibModal') as IModalService; }
  public get MultiselectModel() { return this.$injector.get('MultiselectModel') as any; }
  public get namingService() { return this.$injector.get('namingService') as NamingService; }
  public get notifierService() { return this.$injector.get('notifierService') as NotifierService; }
  public get overrideRegistry() { return this.$injector.get('overrideRegistry') as OverrideRegistry; }
  public get pagerDutyReader() { return this.$injector.get('pagerDutyReader') as PagerDutyReader; }
  public get pagerDutyWriter() { return this.$injector.get('pagerDutyWriter') as PagerDutyWriter; }
  public get pageTitleService() { return this.$injector.get('pageTitleService') as PageTitleService; }
  public get pipelineConfig() { return this.$injector.get('pipelineConfig') as PipelineConfigProvider; }
  public get pipelineConfigService() { return this.$injector.get('pipelineConfigService') as PipelineConfigService; }
  public get pipelineConfigValidator() { return this.$injector.get('pipelineConfigValidator') as PipelineConfigValidator; }
  public get pipelineTemplateService() { return this.$injector.get('pipelineTemplateService') as PipelineTemplateService; }
  public get providerSelectionService() { return this.$injector.get('providerSelectionService') as ProviderSelectionService; }
  public get schedulerFactory() { return this.$injector.get('schedulerFactory') as SchedulerFactory; }
  public get scrollToService() { return this.$injector.get('scrollToService') as ScrollToService; }
  public get recentHistoryService() { return this.$injector.get('recentHistoryService') as RecentHistoryService; }
  public get stateEvents() { return this.$injector.get('stateEvents') as StateEvents; }
  public get taskExecutor() { return this.$injector.get('taskExecutor') as TaskExecutor; }
  public get taskReader() { return this.$injector.get('taskReader') as TaskReader; }
  public get taskMonitorBuilder() { return this.$injector.get('taskMonitorBuilder') as TaskMonitorBuilder; }
  public get urlBuilderService() { return this.$injector.get('urlBuilderService') as UrlBuilderService; }
  public get variableInputService() { return this.$injector.get('variableInputService') as VariableInputService; }
  public get variableValidatorService() { return this.$injector.get('variableValidatorService') as VariableValidatorService; }
  public get versionSelectionService() { return this.$injector.get('versionSelectionService') as VersionSelectionService; }
  public get viewStateCache() { return this.$injector.get('viewStateCache') as ViewStateCacheService; }
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
      promise.promise.catch(() => {});
      $rootScope.$applyAsync(() => {
        promise.transition = originalGo.apply(this, args).then(promise.resolve, promise.reject);
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
