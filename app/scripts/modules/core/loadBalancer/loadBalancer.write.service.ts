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

export interface ILoadBalancerDeleteDescription {
  cloudProvider: string;
  loadBalancerName: string;
  credentials: string;
}

export class LoadBalancerWriter {

  static get $inject() {
    return ['infrastructureCaches', 'taskExecutor'];
  }

  public constructor(private infrastructureCaches: InfrastructureCacheService, private taskExecutor: TaskExecutor) {}

  public deleteLoadBalancer(command: ILoadBalancerDeleteDescription, application: Application): ng.IPromise<ITask> {
    const job: IJob = {
      type: 'deleteLoadBalancer',
    };

    Object.assign(job, command);

    this.infrastructureCaches.clearCache('loadBalancers');

    return this.taskExecutor.executeTask({
      job: [job],
      application: application,
      description: `Delete load balancer: ${command.loadBalancerName}`
    });
  }

  public upsertLoadBalancer(command: ILoadBalancerUpsertDescription, application: Application, descriptor: string, params: any = {}): ng.IPromise<ITask> {
    const job: IJob = {
      type: 'upsertLoadBalancer'
    };

    Object.assign(job, command, params);

    this.infrastructureCaches.clearCache('loadBalancers');

    return this.taskExecutor.executeTask({
      job: [job],
      application: application,
      description: `${descriptor} Load Balancer: ${job['name']}`,
    });
  }
}

export const LOAD_BALANCER_WRITE_SERVICE = 'spinnaker.core.loadBalancer.write.service';
module(LOAD_BALANCER_WRITE_SERVICE, [TASK_EXECUTOR, INFRASTRUCTURE_CACHE_SERVICE])
  .service('loadBalancerWriter', LoadBalancerWriter);
