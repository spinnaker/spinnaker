import {module, IComponentController, IComponentOptions, ILocationService, IOnChangesObject, IScope} from 'angular';
import {IStateService} from 'angular-ui-router';

import {Application} from 'core/application/application.model';
import {IExecutionDetailsStateParams} from 'core/delivery/delivery.states';
import {IExecutionViewState} from 'core/pipeline/config/graph/pipelineGraph.service';
import {IExecution} from 'core/domain/IExecution';
import {IRestartDetails} from 'core/domain/IExecutionStage';
import {CONFIRMATION_MODAL_SERVICE, ConfirmationModalService} from 'core/confirmationModal/confirmationModal.service';
import {CANCEL_MODAL_SERVICE, CancelModalService} from 'core/cancelModal/cancelModal.service';
import {EXECUTION_SERVICE, ExecutionService} from 'core/delivery/service/execution.service';
import {SETTINGS} from 'core/config/settings';

import './execution.less';

class ExecutionController implements IComponentController {
  public application: Application;
  public execution: IExecution;
  public standalone: boolean;

  public showingDetails: boolean;
  public pipelinesUrl: string;
  public filter: any;
  public viewState: IExecutionViewState;
  public sortFilter: any;
  public restartDetails: IRestartDetails;
  private activeRefresher: any;

  static get $inject(): string[] { return ['$scope', '$location', '$stateParams', '$state', 'schedulerFactory',
                                           'ExecutionFilterModel', 'executionService', 'cancelModalService',
                                           'confirmationModalService']; }

  constructor(private $scope: IScope,
              private $location: ILocationService,
              private $stateParams: IExecutionDetailsStateParams,
              private $state: IStateService,
              private schedulerFactory: any,
              private ExecutionFilterModel: any,
              private executionService: ExecutionService,
              private cancelModalService: CancelModalService,
              private confirmationModalService: ConfirmationModalService) {}

  public $onInit(): void {
    this.pipelinesUrl = [SETTINGS.gateUrl, 'pipelines/'].join('/');
    this.sortFilter = this.ExecutionFilterModel.sortFilter;

    this.$scope.$on('$stateChangeSuccess', () => this.updateViewStateDetails());

    this.viewState = {
      activeStageId: Number(this.$stateParams.stage),
      executionId: this.execution.id,
      canTriggerPipelineManually: false,
      canConfigure: false,
      showStageDuration: this.ExecutionFilterModel.sortFilter.showStageDuration,
    };

    let restartedStage = this.execution.stages.find(stage => stage.context.restartDetails !== undefined);
    if (restartedStage) {
      this.restartDetails = restartedStage.context.restartDetails;
    } else {
      this.restartDetails = null;
    }

    if (this.execution.isRunning && !this.standalone) {
      this.activeRefresher = this.schedulerFactory.createScheduler(1000 * Math.ceil(this.execution.stages.length / 10));
      let refreshing = false;
      this.activeRefresher.subscribe(() => {
        if (refreshing) {
          return;
        }
        refreshing = true;
        this.executionService.getExecution(this.execution.id).then((execution: IExecution) => {
          if (!this.$scope.$$destroyed) {
            this.executionService.updateExecution(this.application, execution);
          }
          refreshing = false;
        });
      });
    }

    this.invalidateShowingDetails();
  }

  private updateViewStateDetails(): void {
    this.viewState.activeStageId = Number(this.$stateParams.stage);
    this.viewState.executionId = this.$stateParams.executionId;
    this.invalidateShowingDetails();
  };

  private invalidateShowingDetails(): void {
    this.showingDetails = (this.standalone === true || (this.execution.id === this.$stateParams.executionId &&
      this.$state.includes('**.execution.**')));
  }

  public $onChanges(changesObj: IOnChangesObject): void {
    if (changesObj.standalone || changesObj.execution) {
      this.invalidateShowingDetails();
    }
  }

  public isActive(stageIndex: number): boolean {
    return this.showingDetails && Number(this.$stateParams.stage) === stageIndex;
  }

  public toggleDetails(node: {executionId: string, index: number}): void {
    if (node.executionId === this.$state.params.executionId && this.$state.current.name.includes('.executions.execution') && node.index === undefined) {
      this.$state.go('^');
      return;
    }
    let index = node.index || 0;
    const params = {
      executionId: node.executionId,
      stage: index,
      step: this.execution.stageSummaries[index].firstActiveStage
    };

    if (this.$state.includes('**.execution', params)) {
      if (!this.standalone) {
        this.$state.go('^');
      }
    } else {
      if (this.$state.current.name.includes('.executions.execution') || this.standalone) {
        this.$state.go('.', params);
      } else {
        this.$state.go('.execution', params);
      }
    }
  }

  public getUrl(): string {
    let url = this.$location.absUrl();
    if (!this.standalone) {
      url = url.replace('/executions', '/executions/details');
    }
    return url;
  }

  public deleteExecution(): void {
    this.confirmationModalService.confirm({
      header: 'Really delete execution?',
      buttonText: 'Delete',
      body: '<p>This will permanently delete the execution history.</p>',
      submitMethod: () => this.executionService.deleteExecution(this.application, this.execution.id).then( () => {
        if (this.standalone) {
          this.$state.go('^.^.executions');
        }
      })
    });
  };

  public cancelExecution(): void {
    let hasDeployStage = this.execution.stages && this.execution.stages.some(stage => stage.type === 'deploy' || stage.type === 'cloneServerGroup');
    this.cancelModalService.confirm({
      header: `Really stop execution of ${this.execution.name}?`,
      buttonText: `Stop running ${this.execution.name}`,
      forceable: this.execution.executionEngine === 'v2',
      body: hasDeployStage ? '<b>Note:</b> Any deployments that have begun will continue and need to be cleaned up manually.' : null,
      submitMethod: (reason, force) => this.executionService.cancelExecution(this.application, this.execution.id, force, reason)
    });
  }

  public pauseExecution(): void {
    this.confirmationModalService.confirm({
        header: 'Really pause execution?',
        buttonText: 'Pause',
        body: '<p>This will pause the pipeline for up to 72 hours.</p><p>After 72 hours the pipeline will automatically timeout and fail.</p>',
        submitMethod: () => this.executionService.pauseExecution(this.application, this.execution.id)
    });
  }

  public resumeExecution(): void {
    this.confirmationModalService.confirm({
        header: 'Really resume execution?',
        buttonText: 'Resume',
        submitMethod: () => this.executionService.resumeExecution(this.application, this.execution.id)
    });
  }

  public $onDestroy(): void {
    if (this.activeRefresher) {
      this.activeRefresher.unsubscribe();
    }
  }
}

class ExecutionComponent implements IComponentOptions {
  public bindings: any = {
    application: '<',
    execution: '<',
    standalone: '<'
  };
  public controller: any = ExecutionController;
  public templateUrl = require('./execution.component.html');
}

export const EXECUTION_COMPONENT = 'spinnaker.core.delivery.group.executionGroup.execution.component';
module(EXECUTION_COMPONENT, [
    require('../../filter/executionFilter.model.js'),
    EXECUTION_SERVICE,
    CANCEL_MODAL_SERVICE,
    CONFIRMATION_MODAL_SERVICE,
    require('core/scheduler/scheduler.factory')
])
  .component('execution', new ExecutionComponent());
