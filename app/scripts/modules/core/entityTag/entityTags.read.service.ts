import {module} from 'angular';
import {API_SERVICE, Api} from 'core/api/api.service';
import {IEntityTags, IEntityTag} from '../domain/IEntityTags';

export class EntityTagsReader {

  static get $inject() { return ['API', '$q']; }

  public constructor(private API: Api, private $q: ng.IQService) {}

  public getAllEntityTags(entityType: string, entityIds: string[]): ng.IPromise<IEntityTags[]> {
    const source = this.API.one('tags')
        .withParams({
          entityType: entityType.toLowerCase(),
          entityId: entityIds.join(',')
        }).getList();

    return source.then((entityTags: IEntityTags[]) => {
      entityTags.forEach(entityTag => {
        entityTag.tags.forEach(tag => this.addTagMetadata(entityTag, tag));
        entityTag.alerts = entityTag.tags.filter(t => t.name.startsWith('spinnaker_ui_alert:'));
        entityTag.notices = entityTag.tags.filter(t => t.name.startsWith('spinnaker_ui_notice:'));
      });
      return entityTags;
    });
  }

  private addTagMetadata(entityTag: IEntityTags, tag: IEntityTag): void {
    const metadata = entityTag.tagsMetadata.find(m => m.name === tag.name);
    if (metadata) {
      tag.created = metadata.created;
      tag.createdBy = metadata.createdBy;
      tag.lastModified = metadata.lastModified;
      tag.lastModifiedBy = metadata.lastModifiedBy;
    }
  }
}

export const ENTITY_TAGS_READ_SERVICE = 'spinnaker.core.entityTag.read.service';
module(ENTITY_TAGS_READ_SERVICE, [API_SERVICE])
  .service('entityTagsReader', EntityTagsReader);
