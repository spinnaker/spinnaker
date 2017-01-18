import {module} from 'angular';
import {get} from 'lodash';

import {API_SERVICE, Api} from 'core/api/api.service';
import {IEntityTags, IEntityTag} from '../domain/IEntityTags';

export class EntityTagsReader {

  static get $inject() { return ['API', '$q', '$exceptionHandler', 'settings']; }

  constructor(private API: Api,
                     private $q: ng.IQService,
                     private $exceptionHandler: ng.IExceptionHandlerService,
                     private settings: any) {}

  public getAllEntityTags(entityType: string, entityIds: string[]): ng.IPromise<IEntityTags[]> {
    const idGroups: string[] = this.collateEntityIds(entityType, entityIds);
    const sources = idGroups.map(idGroup => this.API.one('tags')
        .withParams({
          entityType: entityType.toLowerCase(),
          entityId: idGroup
        }).getList()
    );

    let result: ng.IDeferred<IEntityTags[]> = this.$q.defer();

    this.$q.all(sources).then(
      (entityTagGroups: IEntityTags[][]) => {
        const allTags: IEntityTags[] = this.flattenTagsAndAddMetadata(entityTagGroups);
        result.resolve(allTags);
      })
      .catch((error: any) => {
        this.$exceptionHandler(new Error(error), 'Failed to load entity tags');
        result.resolve([]);
      });

    return result.promise;
  }

  private flattenTagsAndAddMetadata(entityTagGroups: IEntityTags[][]): IEntityTags[] {
    const allTags: IEntityTags[] = [];
    entityTagGroups.forEach(entityTagGroup => {
      entityTagGroup.forEach(entityTag => {
        entityTag.tags.forEach(tag => this.addTagMetadata(entityTag, tag));
        entityTag.alerts = entityTag.tags.filter(t => t.name.startsWith('spinnaker_ui_alert:'));
        entityTag.notices = entityTag.tags.filter(t => t.name.startsWith('spinnaker_ui_notice:'));
        allTags.push(entityTag);
      });
    });
    return allTags;
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

  private collateEntityIds(entityType: string, entityIds: string[]): string[] {
    const baseUrlLength = `${this.settings.gateUrl}/tags?entityType=${entityType}&entityIds=`.length;
    const maxIdGroupLength = get(this.settings, 'entityTags.maxUrlLength', 4000) - baseUrlLength;
    const idGroups: string[] = [];
    const joinedEntityIds = entityIds.join(',');

    if (joinedEntityIds.length > maxIdGroupLength) {
      let index = 0,
        currentLength = 0,
        currentGroup: string[] = [];
      while (index < entityIds.length) {
        if (currentLength + entityIds[index].length + 1 > maxIdGroupLength) {
          idGroups.push(currentGroup.join(','));
          currentGroup.length = 0;
          currentLength = 0;
        }
        currentGroup.push(entityIds[index]);
        currentLength += entityIds[index].length + 1;
        index++;
      }
      if (currentGroup.length) {
        idGroups.push(currentGroup.join(','));
      }
    } else {
      idGroups.push(joinedEntityIds);
    }

    return idGroups;
  }
}

export const ENTITY_TAGS_READ_SERVICE = 'spinnaker.core.entityTag.read.service';
module(ENTITY_TAGS_READ_SERVICE, [
  API_SERVICE,
  require('core/config/settings'),
]).service('entityTagsReader', EntityTagsReader);
