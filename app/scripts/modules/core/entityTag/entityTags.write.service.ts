import {module} from 'angular';
import {TASK_EXECUTOR, TaskExecutor} from '../task/taskExecutor';
import {Application} from 'core/application/application.model';
import {IEntityRef, IEntityTags, IEntityTag} from '../domain/IEntityTags';

export class EntityTagWriter {

  static get $inject() { return ['$q', 'taskExecutor']; }

  public constructor(private $q: ng.IQService, private taskExecutor: TaskExecutor) {}

  public upsertEntityTag(application: Application, tag: IEntityTag, entityRef: IEntityRef, isNew: boolean): ng.IPromise<any> {
    return this.taskExecutor.executeTask({
      application: application,
      description: `${isNew ? 'Create' : 'Update'} entity tag on ${entityRef.entityId}`,
      job: [
        {
          type: 'upsertEntityTags',
          application: application.name,
          entityId: entityRef.entityId,
          entityRef: entityRef,
          tags: [tag],
          isPartial: true,
        }
      ]
    });
  }

  public deleteEntityTag(application: Application, owner: any, entityTag: IEntityTags, tag: string) {
    return this.taskExecutor.executeTask({
      application: application,
      description: `Delete entity tag on ${owner.name}`,
      job: [
        {
          type: 'deleteEntityTags',
          application: application.name,
          id: entityTag.id,
          tags: [tag],
        }
      ]
    });
  }
}


export const ENTITY_TAG_WRITER = 'spinnaker.core.entityTag.write.service';
module(ENTITY_TAG_WRITER, [TASK_EXECUTOR])
  .service('entityTagWriter', EntityTagWriter);
