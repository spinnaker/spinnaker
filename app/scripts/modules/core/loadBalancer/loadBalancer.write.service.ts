import {module} from 'angular';

import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';
import {TASK_EXECUTOR, TaskExecutor, IJob} from 'core/task/taskExecutor';
import {Application} from 'core/application/application.model';
import {ITask} from 'core/task/task.read.service';

export interface ILoadBalancerUpsertDescription extends IJob {
  name: string;
  cloudProvider: string;
  healthCheckProtocol?: string;
  healthCheck?: string;
  healthCheckPort?: number;
  healthCheckPath?: string;
}

export interface ILoadBalancerDeleteDescription extends IJob {
  cloudProvider: string;
  loadBalancerName: string;
  credentials: string;
}

export class LoadBalancerWriter {

  public constructor(private infrastructureCaches: InfrastructureCacheService, private taskExecutor: TaskExecutor) {
    'ngInject';
  }

  public deleteLoadBalancer(command: ILoadBalancerDeleteDescription, application: Application): ng.IPromise<ITask> {
    command.type = 'deleteLoadBalancer';

    this.infrastructureCaches.clearCache('loadBalancers');

    return this.taskExecutor.executeTask({
      job: [command],
      application: application,
      description: `Delete load balancer: ${command.loadBalancerName}`
    });
  }

  public upsertLoadBalancer(command: ILoadBalancerUpsertDescription, application: Application, descriptor: string, params: any = {}): ng.IPromise<ITask> {
    Object.assign(command, params);
    command.type = 'upsertLoadBalancer';

    this.infrastructureCaches.clearCache('loadBalancers');

    return this.taskExecutor.executeTask({
      job: [command],
      application: application,
      description: `${descriptor} Load Balancer: ${command['name']}`,
    });
  }
}

export const LOAD_BALANCER_WRITE_SERVICE = 'spinnaker.core.loadBalancer.write.service';
module(LOAD_BALANCER_WRITE_SERVICE, [TASK_EXECUTOR, INFRASTRUCTURE_CACHE_SERVICE])
  .service('loadBalancerWriter', LoadBalancerWriter);
