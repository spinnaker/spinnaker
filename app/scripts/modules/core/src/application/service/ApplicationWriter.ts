import { cloneDeep } from 'lodash';

import { ITask } from '../../domain';
import { RecentHistoryService } from '../../history/recentHistory.service';
import { IJob, TaskExecutor } from '../../task/taskExecutor';

export interface IApplicationAttributes {
  name: string;
  aliases?: string;
  cloudProviders?: string[];
  [k: string]: any;
}

export class ApplicationWriter {
  public static createApplication(application: IApplicationAttributes): PromiseLike<ITask> {
    const jobs: IJob[] = this.buildJobs(application, 'createApplication', cloneDeep);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: 'Create Application: ' + application.name,
    });
  }

  public static updateApplication(application: IApplicationAttributes): PromiseLike<ITask> {
    const jobs: IJob[] = this.buildJobs(application, 'updateApplication', cloneDeep);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: 'Update Application: ' + application.name,
    });
  }

  public static deleteApplication(application: IApplicationAttributes): PromiseLike<ITask> {
    const jobs: IJob[] = this.buildJobs(application, 'deleteApplication', (app: IApplicationAttributes): any => {
      return { name: app.name };
    });
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: 'Deleting Application: ' + application.name,
    })
      .then((task: any): any => {
        RecentHistoryService.removeByAppName(application.name);
        return task;
      })
      .catch((task: any): any => task);
  }

  private static buildJobs(application: IApplicationAttributes, type: string, commandTransformer: any): IJob[] {
    const jobs: IJob[] = [];
    const command = commandTransformer(application);
    if (application.cloudProviders) {
      command.cloudProviders = application.cloudProviders.join(',');
    }
    delete command.accounts;
    jobs.push({
      type,
      application: command,
    });
    return jobs;
  }
}
