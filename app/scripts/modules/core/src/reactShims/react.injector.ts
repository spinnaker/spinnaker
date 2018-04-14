import { IQService, IRootScopeService, IScope } from 'angular';
import IInjectorService = angular.auto.IInjectorService;

import { IModalService } from 'angular-ui-bootstrap';
import { StateParams, StateService, UIRouter } from '@uirouter/core';
import { ApplicationDataSourceRegistry } from 'core/application/service/applicationDataSource.registry';

import { AccountService } from '../account/account.service';
import { Api } from '../api/api.service';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ApplicationReader } from '../application/service/application.read.service';
import { AuthenticationService } from '../authentication/authentication.service';
import { CacheInitializerService } from '../cache/cacheInitializer.service';
import { CancelModalService } from '../cancelModal/cancelModal.service';
import { CloudProviderRegistry } from '../cloudProvider/cloudProvider.registry';
import { ClusterFilterService } from '../cluster/filter/clusterFilter.service';
import { ConfirmationModalService } from '../confirmationModal/confirmationModal.service';
import { EntityTagWriter } from '../entityTag';
import { ExecutionDetailsSectionService } from 'core/pipeline/details/executionDetailsSection.service';
import { ExecutionFilterService } from '../pipeline/filter/executionFilter.service';
import { ExecutionService } from '../pipeline/service/execution.service';
import { ExecutionsTransformerService } from '../pipeline/service/executions.transformer.service';
import { HelpContentsRegistry, IHelpContents } from 'core/help';
import { InfrastructureSearchService } from '../search/infrastructure/infrastructureSearch.service';
import { InfrastructureSearchServiceV2 } from 'core/search/infrastructure/infrastructureSearchV2.service';
import { InsightFilterStateModel } from '../insight/insightFilterState.model';
import { LoadBalancerFilterService } from '../loadBalancer/filter/loadBalancer.filter.service';
import { LoadBalancerWriter } from '../loadBalancer/loadBalancer.write.service';
import { ManualJudgmentService } from '../pipeline/config/stages/manualJudgment/manualJudgment.service';
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
import { RecentHistoryService } from 'core/history/recentHistory.service';
import { SearchService } from '../search/search.service';
import { SchedulerFactory } from '../scheduler/scheduler.factory';
import { ScrollToService } from '../utils/scrollTo/scrollTo.service';
import { SecurityGroupReader } from '../securityGroup/securityGroupReader.service';
import { ServerGroupReader } from '../serverGroup/serverGroupReader.service';
import { ServerGroupWarningMessageService } from '../serverGroup/details/serverGroupWarningMessage.service';
import { ServerGroupWriter } from '../serverGroup/serverGroupWriter.service';
import { StateEvents } from './state.events';
import { SubnetReader } from '../subnet/subnet.read.service';
import { TaskExecutor } from '../task/taskExecutor';
import { TaskMonitorBuilder } from '../task/monitor/taskMonitor.builder';
import { TaskReader } from '../task/task.read.service';
import { UrlBuilderService } from 'core/navigation/urlBuilder.service';
import { VariableInputService } from '../pipeline/config/templates/inputs/variableInput.service';
import { VariableValidatorService } from '../pipeline/config/templates/validators/variableValidator.service';
import { SkinSelectionService } from '../cloudProvider/skinSelection/skinSelection.service';
import { SkinService } from '../cloudProvider/skin.service';
import { WaypointService } from '../utils/waypoints/waypoint.service';

export abstract class ReactInject {
  protected $injector: IInjectorService;

  public abstract initialize($injector: IInjectorService): void;
}

// prettier-ignore
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
  public get applicationDataSourceRegistry() { return this.$injector.get('applicationDataSourceRegistry') as ApplicationDataSourceRegistry; }
  public get applicationModelBuilder() { return this.$injector.get('applicationModelBuilder') as ApplicationModelBuilder; }
  public get applicationReader() { return this.$injector.get('applicationReader') as ApplicationReader; }
  public get authenticationService() { return this.$injector.get('authenticationService') as AuthenticationService; }
  public get cacheInitializer() { return this.$injector.get('cacheInitializer') as CacheInitializerService; }
  public get cancelModalService() { return this.$injector.get('cancelModalService') as CancelModalService; }
  public get cloudProviderRegistry() { return this.$injector.get('cloudProviderRegistry') as CloudProviderRegistry; }
  public get clusterFilterService() { return this.$injector.get('clusterFilterService') as ClusterFilterService; }
  public get confirmationModalService() { return this.$injector.get('confirmationModalService') as ConfirmationModalService; }
  public get entityTagWriter() { return this.$injector.get('entityTagWriter') as EntityTagWriter; }
  public get executionDetailsSectionService() { return this.$injector.get('executionDetailsSectionService') as ExecutionDetailsSectionService; }
  public get executionFilterService() { return this.$injector.get('executionFilterService') as ExecutionFilterService; }
  public get executionService() { return this.$injector.get('executionService') as ExecutionService; }
  public get executionsTransformer() { return this.$injector.get('executionsTransformer') as ExecutionsTransformerService; }
  public get helpContents() { return this.$injector.get('helpContents') as IHelpContents }
  public get helpContentsRegistry() { return this.$injector.get('helpContentsRegistry') as HelpContentsRegistry; }
  public get infrastructureSearchService() { return this.$injector.get('infrastructureSearchService') as InfrastructureSearchService; }
  public get infrastructureSearchServiceV2() { return this.$injector.get('infrastructureSearchServiceV2') as InfrastructureSearchServiceV2; }
  public get insightFilterStateModel() { return this.$injector.get('insightFilterStateModel') as InsightFilterStateModel; }
  public get loadBalancerFilterService() { return this.$injector.get('loadBalancerFilterService') as LoadBalancerFilterService; }
  public get loadBalancerWriter() { return this.$injector.get('loadBalancerWriter') as LoadBalancerWriter; }
  public get manualJudgmentService() { return this.$injector.get('manualJudgmentService') as ManualJudgmentService; }
  public get modalService(): IModalService { return this.$injector.get('$uibModal') as IModalService; }
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
  public get securityGroupReader() { return this.$injector.get('securityGroupReader') as SecurityGroupReader; }
  public get serverGroupReader() { return this.$injector.get('serverGroupReader') as ServerGroupReader; }
  public get serverGroupWarningMessageService() { return this.$injector.get('serverGroupWarningMessageService') as ServerGroupWarningMessageService; }
  public get serverGroupWriter() { return this.$injector.get('serverGroupWriter') as ServerGroupWriter; }
  public get subnetReader() { return this.$injector.get('subnetReader') as SubnetReader; }
  public get recentHistoryService() { return this.$injector.get('recentHistoryService') as RecentHistoryService; }
  public get searchService() { return this.$injector.get('searchService') as SearchService; }
  public get stateEvents() { return this.$injector.get('stateEvents') as StateEvents; }
  public get taskExecutor() { return this.$injector.get('taskExecutor') as TaskExecutor; }
  public get taskReader() { return this.$injector.get('taskReader') as TaskReader; }
  public get taskMonitorBuilder() { return this.$injector.get('taskMonitorBuilder') as TaskMonitorBuilder; }
  public get urlBuilderService() { return this.$injector.get('urlBuilderService') as UrlBuilderService; }
  public get variableInputService() { return this.$injector.get('variableInputService') as VariableInputService; }
  public get variableValidatorService() { return this.$injector.get('variableValidatorService') as VariableValidatorService; }
  public get skinSelectionService() { return this.$injector.get('skinSelectionService') as SkinSelectionService; }
  public get skinService() { return this.$injector.get('skinService') as SkinService; }
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
