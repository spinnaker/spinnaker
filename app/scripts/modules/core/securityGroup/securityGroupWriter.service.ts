import { module } from 'angular';

import { Application } from 'core/application/application.model';
import { INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService } from 'core/cache/infrastructureCaches.service';
import { ISecurityGroup, ITask } from 'core/domain';

import { IJob, TASK_EXECUTOR, TaskExecutor } from 'core/task/taskExecutor';

export interface ISecurityGroupJob extends IJob {
  credentials: string;
  regions: string[];
  securityGroupName: string;
}
export class SecurityGroupWriter {

  constructor(private infrastructureCaches: InfrastructureCacheService,
              private taskExecutor: TaskExecutor) {
    'ngInject';
  }

  public deleteSecurityGroup(securityGroup: ISecurityGroup,
                             application: Application,
                             params: ISecurityGroupJob): ng.IPromise<ITask> {

    params.type = 'deleteSecurityGroup';
    params.securityGroupName = securityGroup.name;
    params.regions = [securityGroup.region];
    params.credentials = securityGroup.accountId;

    const operation: ng.IPromise<ITask> = this.taskExecutor.executeTask({
      job: [params],
      application: application,
      description: `Delete Security Group: ${securityGroup.name}`
    });
    this.infrastructureCaches.clearCache('securityGroups');

    return operation;
  }

  public upsertSecurityGroup(securityGroup: ISecurityGroup,
                             application: Application,
                             description: string,
                             params: any = {}): ng.IPromise<ITask> {

    params.type = 'upsertSecurityGroup';
    params.credentials = securityGroup.credentials || securityGroup.accountName;
    const job: ISecurityGroupJob = Object.assign(securityGroup, params);

    const operation: ng.IPromise<ITask> = this.taskExecutor.executeTask({
      job: [job],
      application: application,
      description: `${description} Security Group: ${securityGroup.name}`
    });

    this.infrastructureCaches.clearCache('securityGroups');

    return operation;
  }
}

export const SECURITY_GROUP_WRITER = 'spinnaker.core.securityGroup.write.service';
module(SECURITY_GROUP_WRITER, [TASK_EXECUTOR, INFRASTRUCTURE_CACHE_SERVICE])
  .service('securityGroupWriter', SecurityGroupWriter);
