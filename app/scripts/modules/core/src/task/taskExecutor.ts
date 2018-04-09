import { IHttpPromiseCallbackArg, IPromise, IQService, module } from 'angular';

import { ITask } from 'core/domain';
import { TASK_READ_SERVICE, TaskReader } from './task.read.service';
import { TASK_WRITE_SERVICE, TaskWriter } from './task.write.service';
import { AUTHENTICATION_SERVICE, AuthenticationService } from '../authentication/authentication.service';

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
  public constructor(
    private $q: IQService,
    private authenticationService: AuthenticationService,
    private taskReader: TaskReader,
    private taskWriter: TaskWriter,
  ) {
    'ngInject';
  }

  public executeTask(taskCommand: ITaskCommand): IPromise<ITask> {
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
    taskCommand.job.forEach(j => (j.user = this.authenticationService.getAuthenticatedUser().name));

    return this.taskWriter.postTaskCommand(taskCommand).then(
      (task: any) => {
        const taskId: string = task.ref.split('/').pop();

        if (owner.runningTasks && owner.runningTasks.refresh) {
          owner.runningTasks.refresh();
        }
        return this.taskReader.getTask(taskId);
      },
      (response: IHttpPromiseCallbackArg<any>) => {
        const error: any = {
          status: response.status,
          message: response.statusText,
        };
        if (response.data && response.data.message) {
          error.log = response.data.message;
        } else {
          error.log = 'Sorry, no more information.';
        }
        return this.$q.reject(error);
      },
    );
  }
}

export const TASK_EXECUTOR = 'spinnaker.core.task.executor';

module(TASK_EXECUTOR, [AUTHENTICATION_SERVICE, TASK_READ_SERVICE, TASK_WRITE_SERVICE]).service(
  'taskExecutor',
  TaskExecutor,
);
