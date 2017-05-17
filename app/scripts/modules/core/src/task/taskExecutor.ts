import { module } from 'angular';

import { ITask } from 'core/domain';
import { TASK_READ_SERVICE, TaskReader } from 'core/task/task.read.service';
import { AUTHENTICATION_SERVICE, AuthenticationService } from '../authentication/authentication.service';

export interface IJob {
  account?: string;
  application?: any;
  providerType?: string;
  source?: any;
  type?: string;
  user?: string;
  [attribute: string]: any;
}

export interface ITaskCommand {
  application?: any;
  project?: any;
  job?: IJob[];
  description?: string;
}

export class TaskExecutor {

  public constructor(private $q: ng.IQService, private authenticationService: AuthenticationService,
                     private taskReader: TaskReader, private taskWriter: any) {
    'ngInject';
  }

  public executeTask(taskCommand: ITaskCommand): ng.IPromise<ITask> {
    const owner: any = taskCommand.application || taskCommand.project || { name: 'ad-hoc'};
    if (taskCommand.application && taskCommand.application.name) {
      taskCommand.application = taskCommand.application.name;
    }
    if (taskCommand.project && taskCommand.project.name) {
      taskCommand.project = taskCommand.project.name;
    }
    if (taskCommand.job[0].providerType === 'aws') {
      delete taskCommand.job[0].providerType;
    }
    taskCommand.job.forEach(j => j.user = this.authenticationService.getAuthenticatedUser().name);

    return this.taskWriter.postTaskCommand(taskCommand).then(
      (task: any) => {
        const taskId: string = task.ref.split('/').pop();

        if (owner.runningTasks && owner.runningTasks.refresh) {
          owner.runningTasks.refresh();
        }
        return this.taskReader.getTask(taskId);
      },
      (response: ng.IHttpPromiseCallbackArg<any>) => {
        const error: any = {
          status: response.status,
          message: response.statusText
        };
        if (response.data && response.data.message) {
          error.log = response.data.message;
        } else {
          error.log = 'Sorry, no more information.';
        }
        return this.$q.reject(error);
      }
    );

  }
}

export const TASK_EXECUTOR = 'spinnaker.core.task.executor';

module(TASK_EXECUTOR, [
  AUTHENTICATION_SERVICE,
  TASK_READ_SERVICE,
  require('./task.write.service.js'),
]).service('taskExecutor', TaskExecutor);
