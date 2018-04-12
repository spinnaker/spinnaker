import { module } from 'angular';

import { Application } from 'core/application/application.model';
import { InfrastructureCaches } from 'core/cache';
import { ITask } from 'core/domain';
import { IJob, TASK_EXECUTOR, TaskExecutor } from 'core/task/taskExecutor';
import { IMoniker } from 'core/naming/IMoniker';

export interface ILoadBalancerUpsertCommand extends IJob {
  name: string;
  cloudProvider: string;
  credentials: string;
  detail?: string;
  healthCheckProtocol?: string;
  healthCheck?: string;
  healthCheckPort?: number;
  healthCheckPath?: string;
  moniker?: IMoniker;
  region: string;
  stack?: string;
}

export interface ILoadBalancerDeleteCommand extends IJob {
  cloudProvider: string;
  loadBalancerName: string;
  credentials: string;
  regions?: string[];
  vpcId?: string;
}

export class LoadBalancerWriter {
  public constructor(private taskExecutor: TaskExecutor) {
    'ngInject';
  }

  public deleteLoadBalancer(command: ILoadBalancerDeleteCommand, application: Application): ng.IPromise<ITask> {
    command.type = 'deleteLoadBalancer';

    InfrastructureCaches.clearCache('loadBalancers');

    return this.taskExecutor.executeTask({
      job: [command],
      application,
      description: `Delete load balancer: ${command.loadBalancerName}`,
    });
  }

  public upsertLoadBalancer(
    command: ILoadBalancerUpsertCommand,
    application: Application,
    descriptor: string,
    params: any = {},
  ): ng.IPromise<ITask> {
    Object.assign(command, params);
    command.type = 'upsertLoadBalancer';

    InfrastructureCaches.clearCache('loadBalancers');

    return this.taskExecutor.executeTask({
      job: [command],
      application,
      description: `${descriptor} Load Balancer: ${command['name']}`,
    });
  }
}

export const LOAD_BALANCER_WRITE_SERVICE = 'spinnaker.core.loadBalancer.write.service';
module(LOAD_BALANCER_WRITE_SERVICE, [TASK_EXECUTOR]).service('loadBalancerWriter', LoadBalancerWriter);
