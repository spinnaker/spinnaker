import { IHttpPromiseCallbackArg } from 'angular';
import { $q } from 'ngimport';

import { AuthenticationService } from '../authentication/AuthenticationService';
import { ITask } from '../domain';
import { TaskReader } from './task.read.service';
import { TaskWriter } from './task.write.service';

export interface IJob {
  [attribute: string]: any;
  account?: string;
  applications?: string[];
  keys?: string[];
  providerType?: string;
  source?: any;
  type?: string;
  user?: string;
}

export interface ITaskCommand {
  application?: any;
  project?: any;
  job?: IJob[];
  description?: string;
}

export class TaskExecutor {
  public static executeTask(taskCommand: ITaskCommand): PromiseLike<ITask> {
    const owner: any = taskCommand.application || taskCommand.project || { name: 'ad-hoc' };
    if (taskCommand.application && taskCommand.application.name) {
      taskCommand.application = taskCommand.application.name;
    }
    if (taskCommand.project && taskCommand.project.name) {
      taskCommand.project = taskCommand.project.name;
    }
    if (taskCommand.job[0].providerType === 'aws') {
      delete taskCommand.job[0].providerType;
    }
    taskCommand.job.forEach((j) => (j.user = AuthenticationService.getAuthenticatedUser().name));

    return TaskWriter.postTaskCommand(taskCommand).then(
      (task: any) => {
        const taskId: string = task.ref.split('/').pop();

        if (owner.runningTasks && owner.runningTasks.refresh) {
          owner.runningTasks.refresh();
        }
        return TaskReader.getTask(taskId);
      },
      (response: IHttpPromiseCallbackArg<any>) => {
        const message: string = response.data?.message || 'Sorry, no more information.';
        const error: any = {
          status: response.status,
          message: response.statusText,
          log: message,
          failureMessage: message,
        };
        return $q.reject(error);
      },
    );
  }
}
