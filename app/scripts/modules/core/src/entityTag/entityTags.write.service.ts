
import { Application } from 'core/application/application.model';
import { IEntityRef, IEntityTag, IEntityTags, ITask } from 'core/domain';
import { TaskExecutor } from 'core/task/taskExecutor';

export class EntityTagWriter {
  public static upsertEntityTag(
    application: Application,
    tag: IEntityTag,
    entityRef: IEntityRef,
    isNew: boolean,
  ): PromiseLike<ITask> {
    return TaskExecutor.executeTask({
      application,
      description: `${isNew ? 'Create' : 'Update'} entity tag on ${entityRef.entityId}`,
      job: [
        {
          type: 'upsertEntityTags',
          application: application.name,
          entityId: entityRef.entityId,
          entityRef,
          tags: [tag],
          isPartial: true,
        },
      ],
    });
  }

  public static deleteEntityTag(application: Application, owner: any, entityTag: IEntityTags, tag: string) {
    return TaskExecutor.executeTask({
      application,
      description: `Delete entity tag on ${owner.name}`,
      job: [
        {
          type: 'deleteEntityTags',
          application: application.name,
          id: entityTag.id,
          tags: [tag],
        },
      ],
    });
  }
}
