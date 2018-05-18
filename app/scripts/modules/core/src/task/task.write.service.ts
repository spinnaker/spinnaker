import { IHttpPromiseCallbackArg, IPromise } from 'angular';

import { API } from 'core/api/ApiService';
import { TaskReader } from './task.read.service';
import { ITaskCommand } from './taskExecutor';
import { DebugWindow } from 'core/utils/consoleDebug';
import { $q, $timeout } from 'ngimport';

export interface ITaskCreateResult {
  ref: string;
}

export class TaskWriter {
  public static postTaskCommand(taskCommand: ITaskCommand): IPromise<ITaskCreateResult> {
    return API.one('applications', taskCommand.application || taskCommand.project)
      .all('tasks')
      .post(taskCommand);
  }

  public static cancelTask(applicationName: string, taskId: string): IPromise<void> {
    return API.one('applications', applicationName)
      .all('tasks')
      .one(taskId, 'cancel')
      .put()
      .then(() =>
        TaskReader.getTask(taskId).then(task =>
          TaskReader.waitUntilTaskMatches(task, updatedTask => updatedTask.status === 'CANCELED'),
        ),
      );
  }

  public static deleteTask(taskId: string): IPromise<void> {
    return API.one('tasks', taskId)
      .remove()
      .then(() => this.waitUntilTaskIsDeleted(taskId));
  }

  private static waitUntilTaskIsDeleted(taskId: string): IPromise<void> {
    // wait until the task is gone, i.e. the promise from the get() is rejected, before succeeding
    const deferred = $q.defer<void>();
    API.one('tasks', taskId)
      .get()
      .then(
        () => {
          $timeout(
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
            $timeout(
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

DebugWindow.TaskWriter = TaskWriter;
