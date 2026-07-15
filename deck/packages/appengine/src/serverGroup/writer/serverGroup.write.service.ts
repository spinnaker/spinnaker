import type { Application, IJob, ITask, ITaskCommand } from '@spinnaker/core';
import { TaskExecutor } from '@spinnaker/core';

import type { IAppengineServerGroupCommand } from '../configure/serverGroupCommandBuilder.service';
import type { IAppengineServerGroup } from '../../domain/index';
import { AppengineDeployDescription } from '../transformer';

interface IAppengineServerGroupWriteJob extends IJob {
  serverGroupName: string;
  region: string;
  credentials: string;
  cloudProvider: string;
}

export class AppengineServerGroupWriter {
  public cloneServerGroup(command: IAppengineServerGroupCommand, application: Application): PromiseLike<ITask> {
    command.type = 'createServerGroup';

    return TaskExecutor.executeTask({
      job: [new AppengineDeployDescription(command)],
      application,
      description: `Create App Engine Server Group in ${application.name}`,
    });
  }

  public startServerGroup(serverGroup: IAppengineServerGroup, application: Application): PromiseLike<ITask> {
    const job = this.buildJob(serverGroup, application, 'startAppEngineServerGroup');

    const command: ITaskCommand = {
      job: [job],
      application,
      description: `Start Server Group: ${serverGroup.name}`,
    };

    return TaskExecutor.executeTask(command);
  }

  public stopServerGroup(serverGroup: IAppengineServerGroup, application: Application): PromiseLike<ITask> {
    const job = this.buildJob(serverGroup, application, 'stopAppEngineServerGroup');

    const command: ITaskCommand = {
      job: [job],
      application,
      description: `Stop Server Group: ${serverGroup.name}`,
    };

    return TaskExecutor.executeTask(command);
  }

  public enableServerGroup(
    serverGroup: IAppengineServerGroup,
    application: Application,
    params: Partial<IAppengineServerGroupWriteJob> = {},
  ): PromiseLike<ITask> {
    const job = { ...params, ...this.buildJob(serverGroup, application, 'enableServerGroup') };

    return TaskExecutor.executeTask({
      job: [job],
      application,
      description: `Enable Server Group: ${serverGroup.name}`,
    });
  }

  public disableServerGroup(
    serverGroup: IAppengineServerGroup,
    application: Application,
    params: Partial<IAppengineServerGroupWriteJob> = {},
  ): PromiseLike<ITask> {
    const job = { ...params, ...this.buildJob(serverGroup, application, 'disableServerGroup') };

    return TaskExecutor.executeTask({
      job: [job],
      application,
      description: `Disable Server Group: ${serverGroup.name}`,
    });
  }

  public destroyServerGroup(
    serverGroup: IAppengineServerGroup,
    application: Application,
    params: Partial<IAppengineServerGroupWriteJob> = {},
  ): PromiseLike<ITask> {
    const job = { ...params, ...this.buildJob(serverGroup, application, 'destroyServerGroup') };

    return TaskExecutor.executeTask({
      job: [job],
      application,
      description: `Destroy Server Group: ${serverGroup.name}`,
    });
  }

  private buildJob(
    serverGroup: IAppengineServerGroup,
    application: Application,
    type: string,
  ): IAppengineServerGroupWriteJob {
    return {
      type,
      region: serverGroup.region,
      serverGroupName: serverGroup.name,
      credentials: serverGroup.account,
      cloudProvider: 'appengine',
      application: application.name,
    };
  }
}
