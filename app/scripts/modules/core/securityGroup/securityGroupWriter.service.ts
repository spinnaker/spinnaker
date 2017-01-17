import {module} from 'angular';

import {TASK_EXECUTOR, TaskExecutor, IJob} from 'core/task/taskExecutor';
import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';
import {ISecurityGroup} from 'core/domain';
import {Application} from 'core/application/application.model';
import {ITask} from 'core/task/task.read.service';

export interface ISecurityGroupJob extends IJob {
  credentials: string;
  regions: string[];
  securityGroupName: string;
}
export class SecurityGroupWriter {

  static get $inject(): string[] {
    return ['infrastructureCaches', 'taskExecutor'];
  }

  constructor(private caches: InfrastructureCacheService,
              private executor: TaskExecutor) {}

  public deleteSecurityGroup(securityGroup: ISecurityGroup,
                             application: Application,
                             params: ISecurityGroupJob): ng.IPromise<ITask> {

    params.type = 'deleteSecurityGroup';
    params.securityGroupName = securityGroup.name;
    params.regions = [securityGroup.region];
    params.credentials = securityGroup.accountId;

    const operation: ng.IPromise<ITask> = this.executor.executeTask({
      job: [params],
      application: application,
      description: `Delete Security Group: ${securityGroup.name}`
    });
    this.caches.clearCache('securityGroups');

    return operation;
  }

  public upsertSecurityGroup(securityGroup: ISecurityGroup,
                             application: Application,
                             description: string,
                             params: any = {}): ng.IPromise<ITask> {

    params.type = 'upsertSecurityGroup';
    params.credentials = securityGroup.credentials || securityGroup.accountName;
    let job: ISecurityGroupJob = Object.assign(params, securityGroup);

    const operation: ng.IPromise<ITask> = this.executor.executeTask({
      job: [job],
      application: application,
      description: `${description} Security Group: ${securityGroup.name}`
    });

    this.caches.clearCache('securityGroups');

    return operation;
  }
}

export const SECURITY_GROUP_WRITER = 'spinnaker.core.securityGroup.write.service';
module(SECURITY_GROUP_WRITER, [TASK_EXECUTOR, INFRASTRUCTURE_CACHE_SERVICE])
  .service('securityGroupWriter', SecurityGroupWriter);
