import {module, IQService, IPromise, IDeferred} from 'angular';
import {countBy, get, uniq} from 'lodash';

import {API_SERVICE, Api} from 'core/api/api.service';
import {IEntityTags, IEntityTag, ICreationMetadataTag} from '../domain/IEntityTags';
import {RETRY_SERVICE, RetryService} from 'core/retry/retry.service';
import {SETTINGS} from 'core/config/settings';

interface ICollatedIdGroup {
  entityId: string;
  maxResults: number;
}

export class EntityTagsReader {

  constructor(private API: Api,
              private $q: IQService,
              private retryService: RetryService) {
    'ngInject';
  }

  public getAllEntityTags(entityType: string, entityIds: string[]): IPromise<IEntityTags[]> {
    if (!entityIds || !entityIds.length) {
      return this.$q.when([]);
    }
    const idGroups: ICollatedIdGroup[] = this.collateEntityIds(entityType, entityIds);
    const succeeded = (val: IEntityTags[]) => val !== null;
    const sources = idGroups.map(idGroup => this.retryService.buildRetrySequence<IEntityTags[]>(
      () => this.API.one('tags')
        .withParams({
          entityType: entityType.toLowerCase(),
          entityId: idGroup.entityId,
          maxResults: idGroup.maxResults,
        }).getList(),
      succeeded, 1, 0)
    );

    const result: IDeferred<IEntityTags[]> = this.$q.defer();

    this.$q.all(sources).then(
      (entityTagGroups: IEntityTags[][]) => {
        const allTags: IEntityTags[] = this.flattenTagsAndAddMetadata(entityTagGroups);
        result.resolve(allTags);
      })
      .catch(() => {
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

  private collateEntityIds(entityType: string, nonUniqueEntityIds: string[]): ICollatedIdGroup[] {
    const entityIds = uniq(nonUniqueEntityIds);
    const entityIdCounts = countBy(nonUniqueEntityIds);
    const baseUrlLength = `${SETTINGS.gateUrl}/tags?entityType=${entityType}&entityIds=`.length;
    const maxIdGroupLength = get(SETTINGS, 'entityTags.maxUrlLength', 4000) - baseUrlLength;
    const idGroups: ICollatedIdGroup[] = [];
    const joinedEntityIds = entityIds.join(',');
    const maxGroupSize = 100;

    if (joinedEntityIds.length > maxIdGroupLength) {
      let index = 0,
        currentLength = 0;
      const currentGroup: string[] = [];
      while (index < entityIds.length) {
        if (currentLength + entityIds[index].length + 1 > maxIdGroupLength || currentGroup.length === maxGroupSize) {
          idGroups.push(this.makeIdGroup(currentGroup, entityIdCounts));
          currentGroup.length = 0;
          currentLength = 0;
        }
        currentGroup.push(entityIds[index]);
        currentLength += entityIds[index].length + 1;
        index++;
      }
      if (currentGroup.length) {
        idGroups.push(this.makeIdGroup(currentGroup, entityIdCounts));
      }
    } else {
      idGroups.push(this.makeIdGroup(entityIds, entityIdCounts));
    }

    return idGroups;
  }

  private makeIdGroup(entityIds: string[], entityIdCounts: {[entityId: string]: number}): ICollatedIdGroup {
    return {
      entityId: entityIds.join(','),
      maxResults: entityIds.reduce((acc, curr) => acc + entityIdCounts[curr], 0)
    };
  }
}

export const ENTITY_TAGS_READ_SERVICE = 'spinnaker.core.entityTag.read.service';
module(ENTITY_TAGS_READ_SERVICE, [
  API_SERVICE,
  RETRY_SERVICE
]).service('entityTagsReader', EntityTagsReader);
