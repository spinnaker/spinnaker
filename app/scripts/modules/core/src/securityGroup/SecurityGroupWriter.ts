import { IPromise } from 'angular';

import { Application } from 'core/application/application.model';
import { InfrastructureCaches } from 'core/cache/infrastructureCaches';
import { ISecurityGroup, ITask } from 'core/domain';
import { FirewallLabels } from './label';

import { IJob, TaskExecutor } from 'core/task/taskExecutor';

export interface ISecurityGroupJob extends IJob {
  credentials: string;
  regions: string[];
  securityGroupName: string;
}
export class SecurityGroupWriter {
  public static deleteSecurityGroup(
    securityGroup: ISecurityGroup,
    application: Application,
    params: ISecurityGroupJob,
  ): IPromise<ITask> {
    params.type = 'deleteSecurityGroup';
    params.securityGroupName = securityGroup.name;
    params.regions = [securityGroup.region];
    params.credentials = securityGroup.accountId;

    const operation: IPromise<ITask> = TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Delete ${FirewallLabels.get('Firewall')}: ${securityGroup.name}`,
    });
    InfrastructureCaches.clearCache('securityGroups');

    return operation;
  }

  public static upsertSecurityGroup(
    securityGroup: ISecurityGroup,
    application: Application,
    description: string,
    params: any = {},
  ): IPromise<ITask> {
    params.type = 'upsertSecurityGroup';
    params.securityGroupName = securityGroup.name;
    params.credentials = securityGroup.credentials || securityGroup.accountName;

    const job: ISecurityGroupJob = { ...securityGroup, ...params };

    const operation: IPromise<ITask> = TaskExecutor.executeTask({
      job: [job],
      application,
      description: `${description} ${FirewallLabels.get('Firewall')}: ${securityGroup.name}`,
    });

    InfrastructureCaches.clearCache('securityGroups');

    return operation;
  }
}
