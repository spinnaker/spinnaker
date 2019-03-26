import { IPromise } from 'angular';

import { IProject, ITask } from 'core/domain';
import { TaskExecutor } from 'core/task/taskExecutor';

export class ProjectWriter {
  public static upsertProject(project: IProject): IPromise<ITask> {
    const descriptor = project.id ? 'Update' : 'Create';
    return TaskExecutor.executeTask({
      application: 'spinnaker',
      job: [
        {
          type: 'upsertProject',
          project: project,
        },
      ],
      project: project,
      description: `${descriptor} project: ${project.name}`,
    });
  }

  public static deleteProject(project: IProject): IPromise<ITask> {
    return TaskExecutor.executeTask({
      application: 'spinnaker',
      job: [
        {
          type: 'deleteProject',
          project: {
            id: project.id,
          },
        },
      ],
      project: project,
      description: 'Delete project: ' + project.name,
    });
  }
}
