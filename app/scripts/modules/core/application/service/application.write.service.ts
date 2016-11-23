import * as _ from 'lodash';
import {Application} from '../application.model';

export interface IJob {
  type: string;
  account: string;
  application: any;
}

export interface IApplicationAttributes {
  name: string;
  accounts: string[];
  cloudProviders?: string[];
  [k: string]: any;
}

export class ApplicationWriter {

  static get $inject() { return ['$q', 'taskExecutor', 'recentHistoryService']; }

  public constructor(private $q: ng.IQService, private taskExecutor: any, private recentHistoryService: any) {}

  public createApplication(application: IApplicationAttributes): ng.IPromise<any> {
    const jobs: IJob[] = this.buildJobs(application, 'createApplication', _.cloneDeep);
    return this.taskExecutor.executeTask({
      job: jobs,
      application: application,
      description: 'Create Application: ' + application.name,
    });
  }

  public updateApplication(application: IApplicationAttributes): ng.IPromise<any> {
    const jobs: IJob[] = this.buildJobs(application, 'updateApplication', _.cloneDeep);
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
        }
      ],
      application: application,
      description: 'Page Application Owner'
    });
  }

  private buildJobs(application: IApplicationAttributes, type: string, commandTransformer: any): IJob[] {
    const jobs: IJob[] = [];
    const command = commandTransformer(application);
    command.accounts = application.accounts.join(',');
    command.cloudProviders = application.cloudProviders ? application.cloudProviders.join(',') : [];
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

angular.module(APPLICATION_WRITE_SERVICE, [
  require('../../task/taskExecutor.js'),
  require('../../history/recentHistory.service.js'),
]).service('applicationWriter', ApplicationWriter);
