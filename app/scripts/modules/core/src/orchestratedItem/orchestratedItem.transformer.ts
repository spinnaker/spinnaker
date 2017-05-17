import { module } from 'angular';
import { duration } from 'moment';

import { IOrchestratedItem, IOrchestratedItemVariable, ITask, ITaskStep } from 'core/domain';

export class OrchestratedItemTransformer {

  public constructor(private $log: ng.ILogService) { 'ngInject'; }

  public addRunningTime(item: any): void {
    // Don't try to add running time more than once - but also don't blow up if something tries to do so
    const testDescriptor: PropertyDescriptor = Object.getOwnPropertyDescriptor(item, 'runningTime');
    if (testDescriptor && !testDescriptor.enumerable) {
      return;
    }
    Object.defineProperties(item, {
      runningTimeInMs: {
        get: this.calculateRunningTime(item)
      }
    });
  }

  public defineProperties(item: any): void {
    // Don't try to add properties more than once - but also don't blow up if something tries to do so
    const testDescriptor: PropertyDescriptor = Object.getOwnPropertyDescriptor(item, 'runningTime');
    if (testDescriptor && !testDescriptor.enumerable) {
      return;
    }

    item.getValueFor = (key: string): any => {
      if (item.context) {
        return item.context[key];
      }
      if (!item.variables) {
        return null;
      }
      const match: IOrchestratedItemVariable = item.variables
        .find((variable: IOrchestratedItemVariable) => variable.key === key);
      return match ? match.value : null;
    };

    item.originalStatus = item.status;

    Object.defineProperties(item, {
      failureMessage: {
        get: (): string => this.getGeneralException(item) || this.getOrchestrationException(item) || null
      },
      isCompleted: {
        get: (): boolean => item.status === 'SUCCEEDED' || item.status === 'SKIPPED'
      },
      isRunning: {
        get: (): boolean => item.status === 'RUNNING'
      },
      isFailed: {
        get: (): boolean => item.status === 'TERMINAL'
      },
      isStopped: {
        get: (): boolean => item.status === 'STOPPED'
      },
      isActive: {
        get: (): boolean => item.status === 'RUNNING' || item.status === 'SUSPENDED' ||
                            item.status === 'NOT_STARTED' || item.status === 'PAUSED'
      },
      hasNotStarted: {
        get: (): boolean => item.status === 'NOT_STARTED'
      },
      isCanceled: {
        get: (): boolean => item.status === 'CANCELED'
      },
      isSuspended: {
        get: (): boolean => item.status === 'SUSPENDED'
      },
      isPaused: {
        get: (): boolean => item.status === 'PAUSED'
      },
      status: {
        // Returns either SUCCEEDED, RUNNING, FAILED, CANCELED, or NOT_STARTED
        get: (): string => this.normalizeStatus(item),
        set: (status) => {
          item.originalStatus = status;
          this.normalizeStatus(item);
        }
      },
      runningTime: {
        get: () => duration(this.calculateRunningTime(item)()).humanize()
      },
      runningTimeInMs: {
        get: this.calculateRunningTime(item)
      }
    });
  }

  private getOrchestrationException(task: ITask): string {
    const katoTasks: any[] = task.getValueFor('kato.tasks');
    if (katoTasks && katoTasks.length) {
      const failedTask: any = katoTasks.find(t => t.status && t.status.failed);
      if (!failedTask) {
        return null;
      }
      const steps: ITaskStep[] = failedTask.history;
      const exception: any = failedTask.exception;
      if (exception) {
        return exception.message;
      }
      if (steps && steps.length) {
        return steps[steps.length - 1].status;
      }
    }
    return null;
  }

  private getGeneralException(task: ITask): string {
    const generalException: any = task.getValueFor('exception');
    if (generalException) {
      if (generalException.details && generalException.details.errors && generalException.details.errors.length) {
        return generalException.details.errors.join(', ');
      }
      if (generalException.details && generalException.details.error) {
        return generalException.details.error;
      }
    }
    return null;
  }

  private calculateRunningTime(item: IOrchestratedItem): () => number {
    return () => {
      if (!item.startTime) {
        return null;
      }
      const normalizedNow: number = Math.max(Date.now(), item.startTime);
      return (item.endTime || normalizedNow) - item.startTime;
    };
  }

  private normalizeStatus(item: IOrchestratedItem): string {
    switch (item.originalStatus) {
      case 'SKIPPED':
        return 'SKIPPED';
      case 'COMPLETED':
      case 'SUCCEEDED':
        return 'SUCCEEDED';
      case 'STARTED':
      case 'EXECUTING':
      case 'RUNNING':
        return 'RUNNING';
      case 'FAILED':
      case 'TERMINAL':
        return 'TERMINAL';
      case 'STOPPED':
        return 'STOPPED';
      case 'SUSPENDED':
      case 'DISABLED':
        return 'SUSPENDED';
      case 'NOT_STARTED':
        return 'NOT_STARTED';
      case 'CANCELED':
        return 'CANCELED';
      case 'UNKNOWN':
        return 'UNKNOWN';
      case 'TERMINATED':
        return 'TERMINATED';
      case 'PAUSED':
        return 'PAUSED';
      case 'FAILED_CONTINUE':
        return 'FAILED_CONTINUE';
      default:
        if (item.originalStatus) {
          this.$log.warn('Unrecognized status:', item.originalStatus);
        }
        return item.originalStatus;
    }
  }
}

export const ORCHESTRATED_ITEM_TRANSFORMER = 'spinnaker.core.orchestratedItem.transformer';
module(ORCHESTRATED_ITEM_TRANSFORMER, []).service('orchestratedItemTransformer', OrchestratedItemTransformer);
