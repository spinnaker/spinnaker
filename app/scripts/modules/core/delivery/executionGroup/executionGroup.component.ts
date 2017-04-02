import {IComponentController, IComponentOptions, IPromise, ITimeoutService, IScope, module} from 'angular';
import {IModalService} from 'angular-ui-bootstrap';
import {IStateService} from 'angular-ui-router';
import {find, flatten, uniq} from 'lodash';

import {Application} from 'core/application/application.model';
import {IExecutionDetailsStateParams} from '../delivery.states';
import {EXECUTION_COMPONENT} from './execution/execution.component';
import {EXECUTION_SERVICE, ExecutionService} from 'core/delivery/service/execution.service';
import {PIPELINE_CONFIG_SERVICE, PipelineConfigService} from 'core/pipeline/config/services/pipelineConfig.service';

import './executionGroup.less';

interface IViewState {
  triggeringExecution: boolean;
  open: boolean;
  poll: IPromise<any>;
  canTriggerPipelineManually: boolean;
  canConfigure: boolean;
  showAccounts: boolean;
};

export class ExecutionGroupController implements IComponentController {
  public group: any;
  public application: Application;

  public deploymentAccounts: string[];
  public viewState: IViewState;
  public pipelineConfig: any;
  public strategyConfig: any;

  static get $inject(): string[] { return ['$scope', '$timeout', '$state', '$stateParams', '$uibModal', 'executionService', 'collapsibleSectionStateCache', 'ExecutionFilterModel', 'pipelineConfigService']; }

  constructor(public $scope: IScope,
              private $timeout: ITimeoutService,
              private $state: IStateService,
              private $stateParams: IExecutionDetailsStateParams,
              private $uibModal: IModalService,
              private executionService: ExecutionService,
              private collapsibleSectionStateCache: any,
              private ExecutionFilterModel: any,
              private pipelineConfigService: PipelineConfigService) {}

  public $onInit(): void {
    this.deploymentAccounts = uniq(flatten<string>(this.group.executions.map((e: any) => e.deploymentTargets))).sort();

    this.pipelineConfig = find(this.application.pipelineConfigs.data, { name: this.group.heading });
    this.strategyConfig = find(this.application.strategyConfigs.data, { name: this.group.heading });

    this.viewState = {
      triggeringExecution: false,
      open: this.isShowingDetails() || !this.collapsibleSectionStateCache.isSet(this.getSectionCacheKey()) || this.collapsibleSectionStateCache.isExpanded(this.getSectionCacheKey()),
      poll: null,
      canTriggerPipelineManually: this.pipelineConfig,
      canConfigure: this.pipelineConfig || this.strategyConfig,
      showAccounts: this.ExecutionFilterModel.sortFilter.groupBy === 'name',
    };

    this.$scope.$on('toggle-expansion', (_event, expanded) => {
      if (this.viewState.open !== expanded) {
        this.toggle();
      }
    });

    this.$scope.$on('$stateChangeSuccess', () => {
      // If the heading is collapsed, but we've clicked on a link to an execution in this group, expand the group
      if (this.isShowingDetails() && !this.viewState.open) {
        this.toggle();
      }
    });
  }

  private showDetails(executionId: string): boolean {
    return executionId === this.$stateParams.executionId &&
      this.$state.includes('**.execution.**');
  }

  private isShowingDetails(): boolean {
    return this.group.executions
          .map((execution: any) => execution.id)
          .some((id: string) => this.showDetails(id));
  }

  public configure(id: string): void {
    if (!this.$state.current.name.includes('.executions.execution')) {
      this.$state.go('^.pipelineConfig', { pipelineId: id });
    } else {
      this.$state.go('^.^.pipelineConfig', { pipelineId: id });
    }
  }

  private hideDetails(): void {
    this.$state.go('.^');
  }

  private getSectionCacheKey(): string {
    return this.executionService.getSectionCacheKey(this.ExecutionFilterModel.sortFilter.groupBy, this.application.name, this.group.heading);
  };

  public toggle(): void {
    this.viewState.open = !this.viewState.open;
    if (this.isShowingDetails()) {
      this.hideDetails();
    }
    this.collapsibleSectionStateCache.setExpanded(this.getSectionCacheKey(), this.viewState.open);
  }

  private startPipeline(command: any): IPromise<void> {
      this.viewState.triggeringExecution = true;
      return this.pipelineConfigService.triggerPipeline(this.application.name, command.pipelineName, command.trigger).then(
        (result: any) => {
          const newPipelineId = result.ref.split('/').pop();
          const monitor = this.executionService.waitUntilNewTriggeredPipelineAppears(this.application, command.pipelineName, newPipelineId);
          monitor.then(() => {
            this.viewState.triggeringExecution = false;
          });
          this.viewState.poll = monitor;
        },
        () => {
          this.viewState.triggeringExecution = false;
        });
  }

  public triggerPipeline(): void {
    this.$uibModal.open({
      templateUrl: require('../manualExecution/manualPipelineExecution.html'),
      controller: 'ManualPipelineExecutionCtrl as vm',
      resolve: {
        pipeline: () => this.pipelineConfig,
        application: () => this.application,
        currentlyRunningExecutions: () => this.group.runningExecutions,
      }
    }).result.then((command) => this.startPipeline(command));
  }

  public $onDestroy(): void {
    if (this.viewState.poll) {
      this.$timeout.cancel(this.viewState.poll);
    }
  }

}

export class ExecutionGroupComponent implements IComponentOptions {
  public bindings: any = {
    group: '<',
    application: '<'
  };
  public controller: any = ExecutionGroupController;
  public templateUrl: string = require('./executionGroup.component.html');
}

export const EXECUTION_GROUP_COMPONENT = 'spinnaker.core.delivery.group.executionGroup.component';
module(EXECUTION_GROUP_COMPONENT, [
  require('../filter/executionFilter.model.js'),
  require('../triggers/triggersTag.directive.js'),
  require('../triggers/nextRun.component'),
  EXECUTION_COMPONENT,
  EXECUTION_FILTER_MODEL,
  EXECUTION_SERVICE,
  PIPELINE_CONFIG_SERVICE
])
.component('executionGroup', new ExecutionGroupComponent());
