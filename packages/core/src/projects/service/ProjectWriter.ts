import { IProject, ITask } from '../../domain';
import { TaskExecutor } from '../../task/taskExecutor';

export class ProjectWriter {
  public static upsertProject(project: IProject): PromiseLike<ITask> {
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

  public static deleteProject(project: IProject): PromiseLike<ITask> {
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
