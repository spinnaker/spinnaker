import {module} from 'angular';
import {get} from 'lodash';

import {API_SERVICE, Api} from 'core/api/api.service';
import {IEntityTags, IEntityTag, ICreationMetadataTag} from '../domain/IEntityTags';
import {RETRY_SERVICE, RetryService} from 'core/retry/retry.service';
import {SETTINGS} from 'core/config/settings';

export class EntityTagsReader {

  static get $inject() { return ['API', '$q', '$exceptionHandler', 'retryService']; }

  constructor(private API: Api,
              private $q: ng.IQService,
              private $exceptionHandler: ng.IExceptionHandlerService,
              private retryService: RetryService) {}

  public getAllEntityTags(entityType: string, entityIds: string[]): ng.IPromise<IEntityTags[]> {
    if (!entityIds || !entityIds.length) {
      return this.$q.when([]);
    }
    const idGroups: string[] = this.collateEntityIds(entityType, entityIds);
    const succeeded = (val: IEntityTags[]) => val !== null;
    const sources = idGroups.map(idGroup => this.retryService.buildRetrySequence<IEntityTags[]>(
      () => this.API.one('tags')
        .withParams({
          entityType: entityType.toLowerCase(),
          entityId: idGroup
        }).getList(),
      succeeded, 1, 0)
    );

    let result: ng.IDeferred<IEntityTags[]> = this.$q.defer();

    this.$q.all(sources).then(
      (entityTagGroups: IEntityTags[][]) => {
        const allTags: IEntityTags[] = this.flattenTagsAndAddMetadata(entityTagGroups);
        result.resolve(allTags);
      })
      .catch(() => {
        this.$exceptionHandler(new Error(`Failed to load ${entityType} entity tags; groups: \n${idGroups.join('\n')}`));
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
        entityTag.creationMetadata = entityTag.tags.find(t => t.name === 'spinnaker:metadata') as ICreationMetadataTag;
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
    const baseUrlLength = `${SETTINGS.gateUrl}/tags?entityType=${entityType}&entityIds=`.length;
    const maxIdGroupLength = get(SETTINGS, 'entityTags.maxUrlLength', 4000) - baseUrlLength;
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
  RETRY_SERVICE
]).service('entityTagsReader', EntityTagsReader);
