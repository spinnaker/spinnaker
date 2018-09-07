import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { API } from 'core/api/ApiService';
import { IEntityTags, IEntityTag, ICreationMetadataTag } from '../domain/IEntityTags';
import { Application } from 'core/application/application.model';
import { IServerGroup, ILoadBalancer, ISecurityGroup } from 'core/domain';
import { SETTINGS } from 'core/config/settings';

export class EntityTagsReader {
  public static getAllEntityTagsForApplication(application: string): IPromise<IEntityTags[]> {
    return API.one('tags')
      .withParams({ application })
      .getList()
      .then((allTags: IEntityTags[]) => this.flattenTagsAndAddMetadata(allTags));
  }

  public static addTagsToServerGroups(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags = application.getDataSource('entityTags').data;
    const serverGroupTags: IEntityTags[] = allTags.filter(t => t.entityRef.entityType === 'servergroup');
    const clusterTags: IEntityTags[] = allTags.filter(t => t.entityRef.entityType === 'cluster');
    application.getDataSource('serverGroups').data.forEach((serverGroup: IServerGroup) => {
      serverGroup.entityTags = serverGroupTags.find(
        t =>
          t.entityRef.entityId === serverGroup.name &&
          t.entityRef.account === serverGroup.account &&
          t.entityRef.region === serverGroup.region,
      );
      serverGroup.clusterEntityTags = clusterTags.filter(
        t =>
          t.entityRef.entityId === serverGroup.cluster &&
          (t.entityRef.account === '*' || t.entityRef.account === serverGroup.account) &&
          (t.entityRef.region === '*' || t.entityRef.region === serverGroup.region),
      );
    });
    application.getDataSource('serverGroups').dataUpdated();
  }

  public static addTagsToLoadBalancers(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags = application.getDataSource('entityTags').data;
    const serverGroupTags: IEntityTags[] = allTags.filter(t => t.entityRef.entityType === 'loadbalancer');
    application.getDataSource('loadBalancers').data.forEach((loadBalancer: ILoadBalancer) => {
      loadBalancer.entityTags = serverGroupTags.find(
        t =>
          t.entityRef.entityId === loadBalancer.name &&
          t.entityRef.account === loadBalancer.account &&
          t.entityRef.region === loadBalancer.region,
      );
    });
    application.getDataSource('loadBalancers').dataUpdated();
  }

  public static addTagsToSecurityGroups(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags = application.getDataSource('entityTags').data;
    const securityGroupTags: IEntityTags[] = allTags.filter(t => t.entityRef.entityType === 'securitygroup');
    application.getDataSource('securityGroups').data.forEach((securityGroup: ISecurityGroup) => {
      securityGroup.entityTags = securityGroupTags.find(
        t =>
          t.entityRef.entityId === securityGroup.name &&
          t.entityRef.account === securityGroup.account &&
          t.entityRef.region === securityGroup.region,
      );
    });
    application.getDataSource('securityGroups').dataUpdated();
  }

  public static getEntityTagsForId(entityType: string, entityId: string): IPromise<IEntityTags[]> {
    if (!entityId) {
      return $q.when([]);
    }
    return API.one('tags')
      .withParams({
        entityType: entityType.toLowerCase(),
        entityId,
      })
      .getList()
      .then((entityTagGroups: IEntityTags[]) => {
        return this.flattenTagsAndAddMetadata(entityTagGroups);
      })
      .catch(() => {
        return $q.when([]);
      });
  }

  private static flattenTagsAndAddMetadata(entityTags: IEntityTags[]): IEntityTags[] {
    const allTags: IEntityTags[] = [];
    entityTags.forEach(entityTag => {
      entityTag.tags.forEach(tag => this.addTagMetadata(entityTag, tag));
      entityTag.alerts = entityTag.tags.filter(t => t.name.startsWith('spinnaker_ui_alert:'));
      entityTag.notices = entityTag.tags.filter(t => t.name.startsWith('spinnaker_ui_notice:'));
      entityTag.creationMetadata = entityTag.tags.find(t => t.name === 'spinnaker:metadata') as ICreationMetadataTag;
      allTags.push(entityTag);
    });
    return allTags;
  }

  private static addTagMetadata(entityTag: IEntityTags, tag: IEntityTag): void {
    const metadata = entityTag.tagsMetadata.find(m => m.name === tag.name);
    if (metadata) {
      tag.created = metadata.created;
      tag.createdBy = metadata.createdBy;
      tag.lastModified = metadata.lastModified;
      tag.lastModifiedBy = metadata.lastModifiedBy;
    }
  }
}
