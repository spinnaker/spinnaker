import { isString } from 'lodash';
import { $q } from 'ngimport';

import { IAccountDetails } from '../account';
import { AccountService } from '../account/AccountService';
import { Application } from '../application';
import { CloudProviderRegistry } from '../cloudProvider';
import { ITask } from '../domain';
import { IJob } from '../task';
import { TaskExecutor } from '../task/taskExecutor';

export class SnapshotWriter {
  private static buildSaveSnapshotJobs(app: Application, accountDetails: IAccountDetails[]): IJob[] {
    const jobs: IJob[] = [];
    accountDetails.forEach((accountDetail) => {
      if (CloudProviderRegistry.getValue(accountDetail.cloudProvider, 'snapshotsEnabled')) {
        jobs.push({
          type: 'saveSnapshot',
          credentials: accountDetail.name,
          applicationName: app.name,
          cloudProvider: accountDetail.cloudProvider,
        });
      }
    });
    return jobs;
  }

  private static buildRestoreSnapshotJob(app: Application, accountDetail: IAccountDetails, timestamp: number) {
    const jobs: IJob[] = [];
    if (CloudProviderRegistry.getValue(accountDetail.cloudProvider, 'snapshotsEnabled')) {
      jobs.push({
        type: 'restoreSnapshot',
        credentials: accountDetail.name,
        applicationName: app.name,
        snapshotTimestamp: timestamp,
        cloudProvider: accountDetail.cloudProvider,
      });
    }
    return jobs;
  }

  private static loadAccountDetails(app: Application): PromiseLike<IAccountDetails[]> {
    const accounts = isString(app.accounts) ? app.accounts.split(',') : [];
    const accountDetailPromises = accounts.map((account) => AccountService.getAccountDetails(account));
    return $q.all(accountDetailPromises);
  }

  public static takeSnapshot(app: Application): PromiseLike<ITask> {
    return this.loadAccountDetails(app).then((accountDetails) => {
      const jobs = this.buildSaveSnapshotJobs(app, accountDetails);
      return TaskExecutor.executeTask({
        job: jobs,
        application: app,
        description: 'Take Snapshot of ' + app.name,
      });
    });
  }

  public static restoreSnapshot(app: Application, account: string, timestamp: number): PromiseLike<ITask> {
    return AccountService.getAccountDetails(account).then((accountDetail) => {
      const jobs = this.buildRestoreSnapshotJob(app, accountDetail, timestamp);
      return TaskExecutor.executeTask({
        job: jobs,
        application: app,
        description: `Restore Snapshot ${timestamp} of application: ${app.name} for account: ${accountDetail.name}`,
      });
    });
  }
}
