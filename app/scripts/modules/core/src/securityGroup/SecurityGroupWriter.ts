import { Application } from '../application/application.model';
import { InfrastructureCaches } from '../cache/infrastructureCaches';
import { ISecurityGroup, ITask } from '../domain';
import { FirewallLabels } from './label';
import { IJob, TaskExecutor } from '../task/taskExecutor';

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
  ): PromiseLike<ITask> {
    params.type = 'deleteSecurityGroup';
    params.securityGroupName = securityGroup.name;
    params.regions = [securityGroup.region];
    params.credentials = securityGroup.accountId;

    const operation: PromiseLike<ITask> = TaskExecutor.executeTask({
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
  ): PromiseLike<ITask> {
    params.type = 'upsertSecurityGroup';
    params.securityGroupName = securityGroup.name;
    params.credentials = securityGroup.credentials || securityGroup.accountName;

    const job: ISecurityGroupJob = { ...securityGroup, ...params };

    const operation: PromiseLike<ITask> = TaskExecutor.executeTask({
      job: [job],
      application,
      description: `${description} ${FirewallLabels.get('Firewall')}: ${securityGroup.name}`,
    });

    InfrastructureCaches.clearCache('securityGroups');

    return operation;
  }
}
