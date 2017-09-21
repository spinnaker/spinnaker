import { IController, IComponentOptions, module, IFilterService } from 'angular';

import { EXECUTION_FILTER_MODEL, ExecutionFilterModel } from 'core/delivery';
import { IExecution } from 'core/domain';
import { IScheduler, SchedulerFactory, SCHEDULER_FACTORY } from 'core/scheduler/scheduler.factory';
import { TIME_FORMATTERS } from 'core/utils/timeFormatters';
import { EXECUTION_USER_FILTER } from './executionUser.filter';

import './executionStatus.less';

export class ExecutionStatusController implements IController {
  public execution: IExecution;
  public toggleDetails: (stageIndex?: number) => void;
  public showingDetails: boolean;
  public standalone: boolean;

  public filter: any;
  public sortFilter: any;
  public parameters: { key: string, value: any }[];
  public timestamp: string;
  public timestampScheduler: IScheduler;

  constructor(private $filter: IFilterService,
              private executionFilterModel: ExecutionFilterModel,
              private schedulerFactory: SchedulerFactory) {
    'ngInject';
  }

  public $onInit(): void {
    // these are internal parameters that are not useful to end users
    const strategyExclusions = [
      'parentPipelineId',
      'strategy',
      'parentStageId',
      'deploymentDetails',
      'cloudProvider'
    ];

    this.filter = this.executionFilterModel.asFilterModel.sortFilter;

    if (this.execution.trigger && this.execution.trigger.parameters) {
      this.parameters = Object.keys(this.execution.trigger.parameters).sort()
        .filter((paramKey) => this.execution.isStrategy ? !strategyExclusions.includes(paramKey) : true)
        .map((paramKey: string) => {
          return { key: paramKey, value: this.execution.trigger.parameters[paramKey] };
        });
    }

    this.timestampScheduler = this.schedulerFactory.createScheduler();
    this.updateTimestamp();
    this.timestampScheduler.subscribe(() => this.updateTimestamp());
  }

  public $onDestroy(): void {
    this.timestampScheduler.unsubscribe();
  }

  private updateTimestamp(): void {
    this.timestamp = this.$filter<Function>('relativeTime')(this.execution.startTime);
  }
}

export class ExecutionStatusComponent implements IComponentOptions {
  public bindings: any = {
    execution: '<',
    toggleDetails: '<',
    showingDetails: '<',
    standalone: '<',
  };
  public controller: any = ExecutionStatusController;
  public templateUrl: string = require('./executionStatus.html');
}

export const EXECUTION_STATUS_COMPONENT = 'spinnaker.core.delivery.executionStatus.component';
module(EXECUTION_STATUS_COMPONENT, [
  EXECUTION_FILTER_MODEL,
  EXECUTION_USER_FILTER,
  SCHEDULER_FACTORY,
  TIME_FORMATTERS,
])
.component('executionStatus', new ExecutionStatusComponent());
