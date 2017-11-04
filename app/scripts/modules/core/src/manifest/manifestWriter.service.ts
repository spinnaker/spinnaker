import { IPromise, module } from 'angular';

import { Application } from 'core/application/application.model';
import { ITask } from 'core/domain';
import { TASK_EXECUTOR, TaskExecutor } from 'core/task/taskExecutor';

export class ManifestWriter {
  constructor(private taskExecutor: TaskExecutor) {
    'ngInject';
  }

  public deployManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Deploy manifest';
    command.type = 'deployManifest';
    return this.taskExecutor.executeTask({
      job: [command],
      application,
      description
    });
  }

  public deleteManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Delete manifest';
    command.type = 'deleteManifest';
    return this.taskExecutor.executeTask({
      job: [command],
      application,
      description
    });
  }
}

export const MANIFEST_WRITER = 'spinnaker.core.manifest.write.service';
module(MANIFEST_WRITER, [
  TASK_EXECUTOR,
]).service('manifestWriter', ManifestWriter);
