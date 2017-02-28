import {cloneDeep} from 'lodash';
import {module} from 'angular';

import {Application} from '../application.model';
import {TASK_EXECUTOR, IJob, TaskExecutor} from 'core/task/taskExecutor';
import {RECENT_HISTORY_SERVICE, RecentHistoryService} from 'core/history/recentHistory.service';

export interface IApplicationAttributes {
  name: string;
  accounts: string[];
  cloudProviders?: string[];
  [k: string]: any;
}

export class ApplicationWriter {

  static get $inject() { return ['taskExecutor', 'recentHistoryService']; }

  public constructor(private taskExecutor: TaskExecutor,
                     private recentHistoryService: RecentHistoryService) {}

  public createApplication(application: IApplicationAttributes): ng.IPromise<any> {
    const jobs: IJob[] = this.buildJobs(application, 'createApplication', cloneDeep);
    return this.taskExecutor.executeTask({
      job: jobs,
      application: application,
      description: 'Create Application: ' + application.name,
    });
  }

  public updateApplication(application: IApplicationAttributes): ng.IPromise<any> {
    const jobs: IJob[] = this.buildJobs(application, 'updateApplication', cloneDeep);
    return this.taskExecutor.executeTask({
      job: jobs,
      application: application,
      description: 'Update Application: ' + application.name,
    });
  }

  public deleteApplication(application: IApplicationAttributes): ng.IPromise<any> {
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

  public pageApplicationOwner(application: Application, reason: string): ng.IPromise<any> {
    return this.taskExecutor.executeTask({
      job: [
        {
          type: 'pageApplicationOwner',
          application: application.name,
          message: reason,
        } as IJob
      ],
      application: application,
      description: 'Page Application Owner'
    });
  }

  private buildJobs(application: IApplicationAttributes, type: string, commandTransformer: any): IJob[] {
    const jobs: IJob[] = [];
    const command = commandTransformer(application);
    command.accounts = application.accounts.join(',');
    if (application.cloudProviders) {
      command.cloudProviders = application.cloudProviders.join(',');
    }
    delete command.account;
    application.accounts.forEach(account => {
      jobs.push({
        type: type,
        account: account,
        application: command,
      });
    });
    return jobs;
  }
}

export const APPLICATION_WRITE_SERVICE = 'spinnaker.core.application.write.service';

module(APPLICATION_WRITE_SERVICE, [
  TASK_EXECUTOR,
  RECENT_HISTORY_SERVICE,
]).service('applicationWriter', ApplicationWriter);
