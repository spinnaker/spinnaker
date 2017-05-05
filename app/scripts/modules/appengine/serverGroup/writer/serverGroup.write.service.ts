import {module} from 'angular';

import {TASK_EXECUTOR, ITaskCommand, TaskExecutor, IJob} from 'core/task/taskExecutor';
import {ITask} from 'core/task/task.read.service';
import {Application} from 'core/application/application.model';
import {IAppengineServerGroup} from 'appengine/domain/index';

interface IAppengineServerGroupWriteJob extends IJob {
  serverGroupName: string;
  region: string;
  credentials: string;
  cloudProvider: string;
}

export class AppengineServerGroupWriter {
  constructor(private taskExecutor: TaskExecutor) { 'ngInject'; }

  public startServerGroup(serverGroup: IAppengineServerGroup, application: Application): ng.IPromise<ITask> {
    const job = this.buildJob(serverGroup, application, 'startAppEngineServerGroup');

    const command: ITaskCommand = {
      job: [job],
      application,
      description: `Start Server Group: ${serverGroup.name}`,
    };

    return this.taskExecutor.executeTask(command);
  }

  public stopServerGroup(serverGroup: IAppengineServerGroup, application: Application): ng.IPromise<ITask> {
    const job = this.buildJob(serverGroup, application, 'stopAppEngineServerGroup');

    const command: ITaskCommand = {
      job: [job],
      application,
      description: `Stop Server Group: ${serverGroup.name}`,
    };

    return this.taskExecutor.executeTask(command);
  }

  private buildJob(serverGroup: IAppengineServerGroup, application: Application, type: string): IAppengineServerGroupWriteJob {
    return {
      type,
      region: serverGroup.region,
      serverGroupName: serverGroup.name,
      credentials: serverGroup.account,
      cloudProvider: 'appengine',
      application: application.name
    };
  }
}

export const APPENGINE_SERVER_GROUP_WRITER = 'spinnaker.appengine.serverGroup.write.service';

module(APPENGINE_SERVER_GROUP_WRITER, [TASK_EXECUTOR])
  .service('appengineServerGroupWriter', AppengineServerGroupWriter);
