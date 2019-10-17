import { IPromise } from 'angular';

import { Application } from 'core/application/application.model';
import { ITask } from 'core/domain';
import { TaskExecutor } from 'core/task/taskExecutor';

export class ManifestWriter {
  public static deployManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Deploy manifest';
    command.type = 'deployManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static deleteManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Delete manifest';
    command.type = 'deleteManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static patchManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Patch a manifest';
    command.type = 'patchManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static scaleManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Scale manifest';
    command.type = 'scaleManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static undoRolloutManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Undo rollout of manifest';
    command.type = 'undoRolloutManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static resumeRolloutManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Resume rollout of manifest';
    command.type = 'resumeRolloutManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static pauseRolloutManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Pause rollout of manifest';
    command.type = 'pauseRolloutManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static rollingRestartManifest(command: any, application: Application): IPromise<ITask> {
    const description = 'Rolling restart of manifest';
    command.type = 'rollingRestartManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static findArtifactsFromResource(command: any, application: Application): IPromise<ITask> {
    const description = 'Find artifacts from a Kubernetes resource';
    command.type = 'findArtifactsFromResource';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }
}
