import { IHttpPromiseCallbackArg, IPromise, IQService, ITimeoutService, module } from 'angular';

import { Api, API_SERVICE } from 'core/api/api.service';
import { TaskReader, TASK_READ_SERVICE } from './task.read.service';
import { ITaskCommand } from './taskExecutor';
import { DebugWindow } from 'core/utils/consoleDebug';

export interface ITaskCreateResult {
  ref: string;
}

export class TaskWriter {
  constructor(
    private API: Api,
    private taskReader: TaskReader,
    private $q: IQService,
    private $timeout: ITimeoutService,
  ) {
    'ngInject';
  }

  public postTaskCommand(taskCommand: ITaskCommand): IPromise<ITaskCreateResult> {
    return this.API.one('applications', taskCommand.application || taskCommand.project)
      .all('tasks')
      .post(taskCommand);
  }

  public cancelTask(applicationName: string, taskId: string): IPromise<void> {
    return this.API.one('applications', applicationName)
      .all('tasks')
      .one(taskId, 'cancel')
      .put()
      .then(() =>
        this.taskReader
          .getTask(taskId)
          .then(task => this.taskReader.waitUntilTaskMatches(task, updatedTask => updatedTask.status === 'CANCELED')),
      );
  }

  public deleteTask(taskId: string): IPromise<void> {
    return this.API.one('tasks', taskId)
      .remove()
      .then(() => this.waitUntilTaskIsDeleted(taskId));
  }

  private waitUntilTaskIsDeleted(taskId: string): IPromise<void> {
    // wait until the task is gone, i.e. the promise from the get() is rejected, before succeeding
    const deferred = this.$q.defer<void>();
    this.API.one('tasks', taskId)
      .get()
      .then(
        () => {
          this.$timeout(
            () => {
              // task is still present, try again
              this.waitUntilTaskIsDeleted(taskId).then(() => deferred.resolve());
            },
            1000,
            false,
          );
        },
        (resp: IHttpPromiseCallbackArg<any>) => {
          if (resp.status === 404) {
            // task is no longer present
            deferred.resolve();
          } else {
            this.$timeout(
              () => {
                // task is maybe still present, try again
                this.waitUntilTaskIsDeleted(taskId).then(deferred.resolve);
              },
              1000,
              false,
            );
          }
        },
      );
    return deferred.promise;
  }
}

export const TASK_WRITE_SERVICE = 'spinnaker.core.task.write.service';
module(TASK_WRITE_SERVICE, [API_SERVICE, TASK_READ_SERVICE]).service('taskWriter', TaskWriter);

DebugWindow.addInjectable('taskWriter');
