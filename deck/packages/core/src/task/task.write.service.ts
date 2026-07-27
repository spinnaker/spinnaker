import { REST } from '../api/ApiService';
import type { ITask } from '../domain';
import { TaskReader } from './task.read.service';
import type { ITaskCommand } from './taskExecutor';
import { DebugWindow } from '../utils/consoleDebug';

export interface ITaskCreateResult {
  ref: string;
}

export class TaskWriter {
  public static postTaskCommand(taskCommand: ITaskCommand): Promise<ITaskCreateResult> {
    return REST('/tasks').post(taskCommand);
  }

  public static cancelTask(taskId: string): Promise<ITask> {
    return REST('/tasks')
      .path(taskId, 'cancel')
      .put()
      .then(() =>
        TaskReader.getTask(taskId).then((task) =>
          TaskReader.waitUntilTaskMatches(task, (updatedTask) => updatedTask.status === 'CANCELED'),
        ),
      );
  }
}

DebugWindow.TaskWriter = TaskWriter;
