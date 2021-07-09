import { Application } from '../application/application.model';
import { ITask } from '../domain';
import { IMoniker } from '../naming/IMoniker';
import { IJob, TaskExecutor } from '../task/taskExecutor';

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
  public static deleteLoadBalancer(command: ILoadBalancerDeleteCommand, application: Application): PromiseLike<ITask> {
    command.type = 'deleteLoadBalancer';

    return TaskExecutor.executeTask({
      job: [command],
      application,
      description: `Delete load balancer: ${command.loadBalancerName}`,
    });
  }

  public static upsertLoadBalancer(
    command: ILoadBalancerUpsertCommand,
    application: Application,
    descriptor: string,
    params: any = {},
  ): PromiseLike<ITask> {
    Object.assign(command, params);
    command.type = 'upsertLoadBalancer';

    return TaskExecutor.executeTask({
      job: [command],
      application,
      description: `${descriptor} Load Balancer: ${command['name']}`,
    });
  }
}
