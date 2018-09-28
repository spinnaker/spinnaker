import { IPromise } from 'angular';

import { API } from 'core/api/ApiService';
import { TaskReader } from './task.read.service';
import { ITaskCommand } from './taskExecutor';
import { DebugWindow } from 'core/utils/consoleDebug';

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
}

DebugWindow.TaskWriter = TaskWriter;
