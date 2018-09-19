import { IPromise } from 'angular';
import { cloneDeep } from 'lodash';

import { ITask } from 'core/domain';
import { IJob, TaskExecutor } from 'core/task/taskExecutor';
import { RecentHistoryService } from 'core/history/recentHistory.service';

export interface IApplicationAttributes {
  name: string;
  aliases?: string;
  cloudProviders?: string[];
  [k: string]: any;
}

export class ApplicationWriter {
  public static createApplication(application: IApplicationAttributes): IPromise<ITask> {
    const jobs: IJob[] = this.buildJobs(application, 'createApplication', cloneDeep);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: 'Create Application: ' + application.name,
    });
  }

  public static updateApplication(application: IApplicationAttributes): IPromise<ITask> {
    const jobs: IJob[] = this.buildJobs(application, 'updateApplication', cloneDeep);
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: 'Update Application: ' + application.name,
    });
  }

  public static deleteApplication(application: IApplicationAttributes): IPromise<ITask> {
    const jobs: IJob[] = this.buildJobs(
      application,
      'deleteApplication',
      (app: IApplicationAttributes): any => {
        return { name: app.name };
      },
    );
    return TaskExecutor.executeTask({
      job: jobs,
      application,
      description: 'Deleting Application: ' + application.name,
    })
      .then(
        (task: any): any => {
          RecentHistoryService.removeByAppName(application.name);
          return task;
        },
      )
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
