import { module } from 'angular';

import { Application, IJob, ITask, ITaskCommand, TaskExecutor } from '@spinnaker/core';
import { IAppengineServerGroup } from '../../domain/index';

interface IAppengineServerGroupWriteJob extends IJob {
  serverGroupName: string;
  region: string;
  credentials: string;
  cloudProvider: string;
}

export class AppengineServerGroupWriter {
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

export const APPENGINE_SERVER_GROUP_WRITER = 'spinnaker.appengine.serverGroup.write.service';

module(APPENGINE_SERVER_GROUP_WRITER, []).service('appengineServerGroupWriter', AppengineServerGroupWriter);
