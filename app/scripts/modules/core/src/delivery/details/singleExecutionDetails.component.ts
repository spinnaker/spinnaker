import { IScope, module } from 'angular';
import { StateService } from '@uirouter/core';
import { Subscription } from 'rxjs/Subscription';

import { SCHEDULER_FACTORY } from 'core/scheduler';
import { Application } from 'core/application';
import { ExecutionFilterModel, EXECUTION_SERVICE, ExecutionService } from 'core/delivery';
import { SchedulerFactory, IScheduler } from 'core/scheduler';
import { IExecution } from 'core/domain';

import './singleExecutionDetails.less';

const template = `
  <div style="padding-top:0">
    <div class="row" ng-if="$ctrl.execution">
      <div class="col-md-10 col-md-offset-1">
        <div class="single-execution-details">
          <div class="flex-container-h baseline">
            <h3>
              <a class="btn btn-configure"
                 ui-sref="^.executions.execution({application: $ctrl.app.name, executionId: $ctrl.execution.id})"
                 uib-tooltip="Back to Executions">
                <span class="glyphicon glyphicon glyphicon-circle-arrow-left"></span>
              </a>
              {{$ctrl.execution.name}}
            </h3>

            <div class="form-group checkbox flex-pull-right">
              <label>
                <input type="checkbox"
                       ng-model="$ctrl.executionFilterModel.showStageDuration"
                       analytics-on="change"
                       analytics-category="Pipelines"
                       analytics-event="Toggle Durations"
                       analytics-label="true">
                stage durations
              </label>
            </div>

            <button class="btn btn-sm btn-default"
                    analytics-on="click"
                    analytics-category="Execution"
                    analytics-event="Configuration"
                    style="margin-right: 5px"
                    uib-tooltip="Navigate to Pipeline Configuration"
                    ui-sref="^.pipelineConfig({application: $ctrl.app.name, pipelineId: $ctrl.execution.pipelineConfigId})">
              <span class="glyphicon glyphicon-cog"></span>
              <span class="visible-md-inline visible-lg-inline">Configure</span>
            </button>
          </div>
        </div>
      </div>
    </div>
    <div class="row" ng-if="$ctrl.execution" >
      <div class="col-md-10 col-md-offset-1 executions">
        <execution execution="$ctrl.execution"
                   application="$ctrl.app"
                   standalone="true"
                   show-stage-duration="$ctrl.executionFilterModel.showStageDuration"
        ></execution>
      </div>
    </div>
    <div class="row" ng-if="$ctrl.stateNotFound" style="min-height: 300px">
      <h4 class="text-center">
        <p>The execution cannot be found.</p>
        <a ui-sref="^.executions.execution({application: $ctrl.app.name})">Back to Executions.</span>
        </a>
      </h4>
    </div>
  </div>
`;

class SingleExecutionDetailsController {
  private executionScheduler: IScheduler;
  private executionLoader: Subscription;

  private execution: IExecution;
  private stateNotFound: boolean;
  private app: Application;

  constructor(private schedulerFactory: SchedulerFactory,
              private $state: StateService,
              private executionService: ExecutionService,
              public executionFilterModel: ExecutionFilterModel,
              $scope: IScope) {
    'ngInject';
    $scope.$on('$stateChangeSuccess', (_event, _toState, toParams, _fromState, fromParams) => {
      if (toParams.application !== fromParams.application || toParams.executionId !== fromParams.executionId) {
        this.getExecution();
      }
    });
  }

  public $onInit() {
    this.executionScheduler = this.schedulerFactory.createScheduler(5000);
    this.executionLoader = this.executionScheduler.subscribe(() => this.getExecution());

    this.getExecution();
  }

  public $onDestroy() {
    this.executionScheduler.unsubscribe();
    this.executionLoader.unsubscribe();
  }

  private getExecution() {
    const { executionService, executionScheduler, executionLoader, $state } = this;

    if (this.app.notFound) {
      return;
    }

    executionService.getExecution($state.params.executionId).then((execution) => {
      this.execution = this.execution || execution;
      executionService.transformExecution(this.app, execution);
      executionService.synchronizeExecution(this.execution, execution);
      if (!execution.isActive) {
        executionScheduler.unsubscribe();
        executionLoader.unsubscribe();
      }
    }, () => {
      this.execution = null;
      this.stateNotFound = true;
    });
  };
}

export const CORE_DELIVERY_DETAILS_SINGLEEXECUTIONDETAILS = 'spinnaker.core.singleExecutionDetails';
module(CORE_DELIVERY_DETAILS_SINGLEEXECUTIONDETAILS, [
  EXECUTION_SERVICE,
  SCHEDULER_FACTORY,
])
.component('singleExecutionDetails', {
  template,
  controller: SingleExecutionDetailsController,
  bindings: { app: '<' }
});

