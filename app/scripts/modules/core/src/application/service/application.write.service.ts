import { IPromise, module } from 'angular';
import { cloneDeep } from 'lodash';

import { TASK_EXECUTOR, IJob, TaskExecutor } from 'core/task/taskExecutor';
import { RECENT_HISTORY_SERVICE, RecentHistoryService } from 'core/history/recentHistory.service';

export interface IApplicationAttributes {
  name: string;
  cloudProviders?: string[];
  [k: string]: any;
}

export class ApplicationWriter {

  public constructor(private taskExecutor: TaskExecutor,
                     private recentHistoryService: RecentHistoryService) {
    'ngInject';
  }

  public createApplication(application: IApplicationAttributes): IPromise<any> {
    const jobs: IJob[] = this.buildJobs(application, 'createApplication', cloneDeep);
    return this.taskExecutor.executeTask({
      job: jobs,
      application: application,
      description: 'Create Application: ' + application.name,
    });
  }

  public updateApplication(application: IApplicationAttributes): IPromise<any> {
    const jobs: IJob[] = this.buildJobs(application, 'updateApplication', cloneDeep);
    return this.taskExecutor.executeTask({
      job: jobs,
      application: application,
      description: 'Update Application: ' + application.name,
    });
  }

  public deleteApplication(application: IApplicationAttributes): IPromise<any> {
    const jobs: IJob[] = this.buildJobs(application, 'deleteApplication', (app: IApplicationAttributes): any => { return { name: app.name }; });
    return this.taskExecutor.executeTask({
      job: jobs,
      application: application,
      description: 'Deleting Application: ' + application.name
    })
      .then((task: any): any => {
        this.recentHistoryService.removeByAppName(application.name);
        return task;
      })
      .catch((task: any): any => task);
  }

  private buildJobs(application: IApplicationAttributes, type: string, commandTransformer: any): IJob[] {
    const jobs: IJob[] = [];
    const command = commandTransformer(application);
    if (application.cloudProviders) {
      command.cloudProviders = application.cloudProviders.join(',');
    }
    delete command.accounts;
    jobs.push({
      type: type,
      application: command,
    });
    return jobs;
  }
}

export const APPLICATION_WRITE_SERVICE = 'spinnaker.core.application.write.service';

module(APPLICATION_WRITE_SERVICE, [
  TASK_EXECUTOR,
  RECENT_HISTORY_SERVICE,
]).service('applicationWriter', ApplicationWriter);
