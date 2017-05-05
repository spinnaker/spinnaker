import {module} from 'angular';

import {ITask} from 'core/task/task.read.service';
import {ServerGroup, ISecurityGroup} from 'core/domain';
import {TASK_EXECUTOR, IJob, TaskExecutor} from 'core/task/taskExecutor';
import {Application} from 'core/application/application.model';
import {NAMING_SERVICE, NamingService} from 'core/naming/naming.service';

interface ISource {
  asgName: string;
}

interface IViewState {
  mode: string;
}

interface ICapacity {
  desired: number;
  max: number;
  min: number;
}

export interface IServerGroupCommand {
  application?: Application;
  freeFormDetails?: string;
  source?: ISource;
  stack?: string;
  type?: string;
  viewState?: IViewState;
}

export interface IServerGroupJob extends IJob {
  amiName?: string;
  asgName?: string;
  capacity?: ICapacity;
  credentials?: string;
  cloudProvider?: string;
  region?: string;
  securityGroups?: string[];
  serverGroupName?: string;
  type?: string;
}

export class ServerGroupWriter {

  constructor(private namingService: NamingService,
              private taskExecutor: TaskExecutor,
              private serverGroupTransformer: any) {
    'ngInject';
  }

  public cloneServerGroup(command: IServerGroupCommand,
                          application: Application): ng.IPromise<ITask> {

    let description: string;
    if (command.viewState.mode === 'clone') {
      description = `Create Cloned Server Group from ${command.source.asgName}`;
      command.type = 'cloneServerGroup';
    } else {
      command.type = 'createServerGroup';
      description =
        `Create New Server Group in cluster ${this.namingService.getClusterName(
          application.name,
          command.stack,
          command.freeFormDetails)}`;
    }

    return this.taskExecutor.executeTask({
      job: [this.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command)],
      application,
      description
    });
  }

  public destroyServerGroup(serverGroup: ServerGroup,
                            application: Application,
                            params: IServerGroupJob = {}): ng.IPromise<ITask> {

    params.asgName = serverGroup.name;
    params.serverGroupName = serverGroup.name;
    params.type = 'destroyServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return this.taskExecutor.executeTask({
      job: [params],
      application,
      description: `Destroy Server Group: ${serverGroup.name}`
    });
  }

  public disableServerGroup(serverGroup: ServerGroup,
                            appName: string,
                            params: IServerGroupJob = {}): ng.IPromise<ITask> {

    params.asgName = serverGroup.name;
    params.serverGroupName = serverGroup.name;
    params.type = 'disableServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return this.taskExecutor.executeTask({
      job: [params],
      application: appName,
      description: `Disable Server Group: ${serverGroup.name}`
    });
  }

  public enableServerGroup(serverGroup: ServerGroup,
                           application: Application,
                           params: IServerGroupJob = {}): ng.IPromise<ITask> {

    params.asgName = serverGroup.name;
    params.serverGroupName = serverGroup.name;
    params.type = 'enableServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return this.taskExecutor.executeTask({
      job: [params],
      application,
      description: `Enable Server Group: ${serverGroup.name}`
    });
  }

  public resizeServerGroup(serverGroup: ServerGroup,
                           application: Application,
                           params: IServerGroupJob = {}): ng.IPromise<ITask> {

    params.asgName = serverGroup.name;
    params.serverGroupName = serverGroup.name;
    params.type = 'resizeServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return this.taskExecutor.executeTask({
      job: [params],
      application,
      description: `Resize Server Group: ${serverGroup.name} to ${params.capacity.min}/${params.capacity.desired}/${params.capacity.max}`
    });
  }

  public rollbackServerGroup(serverGroup: ServerGroup,
                             application: Application,
                             params: IServerGroupJob = {}): ng.IPromise<ITask> {

    params.type = 'rollbackServerGroup';
    params.region = serverGroup.region;
    params.credentials = serverGroup.account;
    params.cloudProvider = serverGroup.type || serverGroup.provider;

    return this.taskExecutor.executeTask({
      job: [params],
      application,
      description: `Rollback Server Group: ${serverGroup.name}`
    });
  }

  public updateSecurityGroups(serverGroup: ServerGroup,
                              securityGroups: ISecurityGroup[],
                              application: Application): ng.IPromise<ITask> {

    const job: IServerGroupJob = {
      amiName: serverGroup.launchConfig.imageId,
      cloudProvider: serverGroup.type || serverGroup.provider,
      credentials: serverGroup.account,
      region: serverGroup.region,
      securityGroups: securityGroups.map((group: ISecurityGroup) => group.id),
      serverGroupName: serverGroup.name,
      type: 'updateSecurityGroupsForServerGroup'
    };

    return this.taskExecutor.executeTask({
      job: [job],
      application,
      description: `Update security groups for ${serverGroup.name}`
    });
  }
}

export const SERVER_GROUP_WRITER = 'spinnaker.core.serverGroup.write.service';
module(SERVER_GROUP_WRITER, [
  NAMING_SERVICE,
  TASK_EXECUTOR,
  require('./serverGroup.transformer.js')
])
  .service('serverGroupWriter', ServerGroupWriter);
