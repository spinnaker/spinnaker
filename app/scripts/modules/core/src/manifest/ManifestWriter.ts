import { Application } from '../application/application.model';
import { ITask } from '../domain';
import { TaskExecutor } from '../task/taskExecutor';

export class ManifestWriter {
  public static deployManifest(command: any, application: Application): PromiseLike<ITask> {
    const description = 'Deploy manifest';
    command.type = 'deployManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static deleteManifest(command: any, application: Application): PromiseLike<ITask> {
    const description = 'Delete manifest';
    command.type = 'deleteManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static scaleManifest(command: any, application: Application): PromiseLike<ITask> {
    const description = 'Scale manifest';
    command.type = 'scaleManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static undoRolloutManifest(command: any, application: Application): PromiseLike<ITask> {
    const description = 'Undo rollout of manifest';
    command.type = 'undoRolloutManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static resumeRolloutManifest(command: any, application: Application): PromiseLike<ITask> {
    const description = 'Resume rollout of manifest';
    command.type = 'resumeRolloutManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static pauseRolloutManifest(command: any, application: Application): PromiseLike<ITask> {
    const description = 'Pause rollout of manifest';
    command.type = 'pauseRolloutManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }

  public static rollingRestartManifest(command: any, application: Application): PromiseLike<ITask> {
    const description = 'Rolling restart of manifest';
    command.type = 'rollingRestartManifest';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description,
    });
  }
}
