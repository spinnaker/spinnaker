import { module } from 'angular';

import { Application } from '../application/application.model';
import { IServerGroupCommand } from './configure/common/serverGroupCommandBuilder.service';
import { ISecurityGroup, IServerGroup, ITask } from '../domain';
import { IMoniker, NameUtils } from '../naming';
import { FirewallLabels } from '../securityGroup/label';
import { CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER } from './serverGroup.transformer';
import { IJob, TaskExecutor } from '../task/taskExecutor';

export interface ICapacity {
  desired: number | string;
  max: number | string;
  min: number | string;
}

export interface IServerGroupJob extends IJob {
  amiName?: string;
  asgName?: string;
  capacity?: Partial<ICapacity>;
  credentials?: string;
  cloudProvider?: string;
  region?: string;
  securityGroups?: string[];
  serverGroupName?: string;
  type?: string;
  moniker?: IMoniker;
}

export class ServerGroupWriter {
  public static $inject = ['serverGroupTransformer'];
  constructor(private serverGroupTransformer: any) {}

  public cloneServerGroup(command: IServerGroupCommand, application: Application): PromiseLike<ITask> {
    let description: string;
    if (command.viewState.mode === 'clone') {
      description = `Create Cloned Server Group from ${command.source.asgName}`;
      command.type = 'cloneServerGroup';
    } else {
      command.type = 'createServerGroup';
      description = `Create New Server Group in cluster ${NameUtils.getClusterName(
        application.name,
        command.stack,
        command.freeFormDetails,
      )}`;
    }

    return TaskExecutor.executeTask({
      job: [this.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command)],
      application,
      description,
    });
  }

  public destroyServerGroup(
    serverGroup: IServerGroup,
    application: Application,
    params: IServerGroupJob = {},
  ): PromiseLike<ITask> {
    params.asgName = serverGroup.name;
    params.moniker = serverGroup.moniker;
    params.serverGroupName = serverGroup.name;
    params.type = 'destroyServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Destroy Server Group: ${serverGroup.name}`,
    });
  }

  public disableServerGroup(
    serverGroup: IServerGroup,
    appName: string,
    params: IServerGroupJob = {},
  ): PromiseLike<ITask> {
    params.asgName = serverGroup.name;
    params.serverGroupName = serverGroup.name;
    params.moniker = serverGroup.moniker;
    params.type = 'disableServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return TaskExecutor.executeTask({
      job: [params],
      application: appName,
      description: `Disable Server Group: ${serverGroup.name}`,
    });
  }

  public enableServerGroup(
    serverGroup: IServerGroup,
    application: Application,
    params: IServerGroupJob = {},
  ): PromiseLike<ITask> {
    params.asgName = serverGroup.name;
    params.serverGroupName = serverGroup.name;
    params.moniker = serverGroup.moniker;
    params.type = 'enableServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Enable Server Group: ${serverGroup.name}`,
    });
  }

  private getCapacityString(capacity: Partial<ICapacity>): string {
    if (!capacity) {
      return null;
    }
    return Object.keys(capacity)
      .map((k: keyof ICapacity) => `${k}: ${capacity[k]}`)
      .join(', ');
  }

  public mapLoadBalancers(serverGroup: IServerGroup, application: Application, params: any = {}): PromiseLike<ITask> {
    params.type = 'mapLoadBalancers';
    params.name = [serverGroup.name];
    params.loadBalancerNames = serverGroup.loadBalancers;
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.cloudProvider;
    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Map load balancers for server group: ${serverGroup.name}`,
    });
  }

  public resizeServerGroup(
    serverGroup: IServerGroup,
    application: Application,
    params: IServerGroupJob = {},
  ): PromiseLike<ITask> {
    params.asgName = serverGroup.name;
    params.serverGroupName = serverGroup.name;
    params.moniker = serverGroup.moniker;
    params.type = 'resizeServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;
    const currentSize: string = this.getCapacityString(serverGroup.capacity);
    const newSize: string = this.getCapacityString(params.capacity);
    const currentSizeText = currentSize ? ` from (${currentSize}) ` : ' ';

    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Resize Server Group: ${serverGroup.name}${currentSizeText}to (${newSize})`,
    });
  }

  public rollbackServerGroup(
    serverGroup: IServerGroup,
    application: Application,
    params: IServerGroupJob = {},
  ): PromiseLike<ITask> {
    params.type = 'rollbackServerGroup';
    params.moniker = serverGroup.moniker;
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Rollback Server Group: ${serverGroup.name}`,
    });
  }

  public unmapLoadBalancers(serverGroup: IServerGroup, application: Application, params: any = {}): PromiseLike<ITask> {
    params.type = 'unmapLoadBalancers';
    params.name = [serverGroup.name];
    params.loadBalancerNames = serverGroup.loadBalancers;
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.cloudProvider;
    return TaskExecutor.executeTask({
      job: [params],
      application,
      description: `Unmap load balancers for server group: ${serverGroup.name}`,
    });
  }

  public updateSecurityGroups(
    serverGroup: IServerGroup,
    securityGroups: ISecurityGroup[],
    application: Application,
    hasLaunchTemplate?: boolean,
  ): PromiseLike<ITask> {
    const job: IServerGroupJob = {
      amiName: serverGroup.image.name,
      imageId: serverGroup.image.imageId,
      moniker: serverGroup.moniker,
      cloudProvider: serverGroup.type || serverGroup.provider,
      credentials: serverGroup.account,
      region: serverGroup.region,
      securityGroups: securityGroups.map((group: ISecurityGroup) => group.id),
      serverGroupName: serverGroup.name,
      type: 'updateSecurityGroupsForServerGroup',
    };

    if (hasLaunchTemplate) {
      job.hasLaunchTemplate = hasLaunchTemplate;
    }

    return TaskExecutor.executeTask({
      job: [job],
      application,
      description: `Update ${FirewallLabels.get('firewalls')} for ${serverGroup.name}`,
    });
  }
}

export const SERVER_GROUP_WRITER = 'spinnaker.core.serverGroup.write.service';
module(SERVER_GROUP_WRITER, [CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER]).service('serverGroupWriter', ServerGroupWriter);
