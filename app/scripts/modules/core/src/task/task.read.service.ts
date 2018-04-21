import { module } from 'angular';
import { $log, $q, $timeout } from 'ngimport';

import { API } from 'core/api/ApiService';
import { OrchestratedItemTransformer } from 'core/orchestratedItem/orchestratedItem.transformer';
import { ITask } from 'core/domain';

export class TaskReader {
  private activeStatuses: string[] = ['RUNNING', 'SUSPENDED', 'NOT_STARTED'];

  public getTasks(applicationName: string, statuses: string[] = []): ng.IPromise<ITask[]> {
    return API.one('applications', applicationName)
      .all('tasks')
      .getList({ statuses: statuses.join(',') })
      .then((tasks: ITask[]) => {
        tasks.forEach(task => this.setTaskProperties(task));
        return tasks.filter(task => !task.getValueFor('dryRun'));
      });
  }

  public getRunningTasks(applicationName: string): ng.IPromise<ITask[]> {
    return this.getTasks(applicationName, this.activeStatuses);
  }

  public getTask(taskId: string): ng.IPromise<ITask> {
    return API.one('tasks', taskId)
      .get()
      .then((task: ITask) => {
        OrchestratedItemTransformer.defineProperties(task);
        if (task.steps && task.steps.length) {
          task.steps.forEach(step => OrchestratedItemTransformer.defineProperties(step));
        }
        if (task.execution) {
          OrchestratedItemTransformer.defineProperties(task.execution);
          if (task.execution.stages) {
            task.execution.stages.forEach((stage: any) => OrchestratedItemTransformer.defineProperties(stage));
          }
        }
        this.setTaskProperties(task);
        return task;
      })
      .catch((error: any) => $log.warn('There was an issue retrieving taskId: ', taskId, error));
  }

  public waitUntilTaskMatches(
    task: ITask,
    closure: (task: ITask) => boolean,
    failureClosure?: (task: ITask) => boolean,
    interval = 1000,
  ): ng.IPromise<ITask> {
    const deferred = $q.defer<ITask>();
    if (!task) {
      deferred.reject(null);
    } else if (closure(task)) {
      deferred.resolve(task);
    } else if (failureClosure && failureClosure(task)) {
      deferred.reject(task);
    } else {
      task.poller = $timeout(() => {
        this.getTask(task.id).then(updated => {
          this.updateTask(task, updated);
          this.waitUntilTaskMatches(task, closure, failureClosure, interval).then(deferred.resolve, deferred.reject);
        });
      }, interval);
    }
    return deferred.promise;
  }

  public waitUntilTaskCompletes(task: ITask, interval = 1000): ng.IPromise<ITask> {
    return this.waitUntilTaskMatches(task, t => t.isCompleted, t => t.isFailed, interval);
  }

  /**
   * When polling for a match, (most of) the new task's properties are copied into the original task; if you need
   * some other property, you'll need to update this method
   */
  private updateTask(original: ITask, updated?: ITask): void {
    if (!updated) {
      return;
    }
    original.status = updated.status;
    original.variables = updated.variables;
    original.steps = updated.steps;
    original.endTime = updated.endTime;
    original.execution = updated.execution;
    original.history = updated.history;
  }

  private setTaskProperties(task: ITask): void {
    OrchestratedItemTransformer.defineProperties(task);
    if (task.steps && task.steps.length) {
      task.steps.forEach(step => OrchestratedItemTransformer.defineProperties(step));
    }
  }
}

export const TASK_READ_SERVICE = 'spinnaker.core.task.read.service';

module(TASK_READ_SERVICE, []).service('taskReader', TaskReader);
