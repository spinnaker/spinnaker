import {module} from 'angular';
import {TASK_EXECUTOR, TaskExecutor} from '../task/taskExecutor';
import {Application} from 'core/application/application.model';
import {EntityRefBuilder} from './entityRef.builder';
import {IEntityRef, IEntityTags, IEntityTag} from '../domain/IEntityTags';

export class EntityTagWriter {

  static get $inject() { return ['$q', 'taskExecutor']; }

  public constructor(private $q: ng.IQService, private taskExecutor: TaskExecutor) {}

  public upsertEntityTag(application: Application, tag: IEntityTag, owner: any, entityType: string, isNew: boolean): ng.IPromise<any> {
    const refBuilder: (entity: any) => IEntityRef = EntityRefBuilder.getBuilder(entityType);
    if (refBuilder) {
      return this.taskExecutor.executeTask({
        application: application,
        description: `${isNew ? 'Create' : 'Update'} entity tag on ${owner.name}`,
        job: [
          {
            type: 'upsertEntityTags',
            application: application.name,
            entityId: owner.name,
            entityRef: refBuilder(owner),
            tags: [tag],
            isPartial: true,
          }
        ]
      });
    }
    return this.$q.reject(`No processor found for entity type: ${entityType}`);
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
