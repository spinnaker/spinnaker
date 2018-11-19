import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { API } from 'core/api/ApiService';
import { IEntityTags, IEntityTag, ICreationMetadataTag } from '../domain/IEntityTags';
import { Application } from 'core/application/application.model';
import { IExecution, IPipeline, IServerGroup, IServerGroupManager, ILoadBalancer, ISecurityGroup } from 'core/domain';
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
  }

  public static addTagsToServerGroupManagers(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags = application.getDataSource('entityTags').data;
    const serverGroupManagerTags: IEntityTags[] = allTags.filter(t => t.entityRef.entityType === 'servergroupmanager');
    application.getDataSource('serverGroupManagers').data.forEach((serverGroupManager: IServerGroupManager) => {
      serverGroupManager.entityTags = serverGroupManagerTags.find(
        t =>
          t.entityRef.entityId === serverGroupManager.name &&
          t.entityRef.account === serverGroupManager.account &&
          t.entityRef.region === serverGroupManager.region,
      );
    });
  }

  public static addTagsToLoadBalancers(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags = application.getDataSource('entityTags').data;
    const loadBalancerTags: IEntityTags[] = allTags.filter(t => t.entityRef.entityType === 'loadbalancer');
    application.getDataSource('loadBalancers').data.forEach((loadBalancer: ILoadBalancer) => {
      loadBalancer.entityTags = loadBalancerTags.find(
        t =>
          t.entityRef.entityId === loadBalancer.name &&
          t.entityRef.account === loadBalancer.account &&
          t.entityRef.region === loadBalancer.region,
      );
    });
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
  }

  public static addTagsToExecutions(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags = application.getDataSource('entityTags').data;
    const executionTags: IEntityTags[] = allTags.filter(t => t.entityRef.entityType === 'execution');
    application.getDataSource('executions').data.forEach((execution: IExecution) => {
      execution.entityTags = executionTags.find(t => t.entityRef.entityId === execution.id);
    });
  }

  public static addTagsToPipelines(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags = application.getDataSource('entityTags').data;
    const pipelineTags: IEntityTags[] = allTags.filter(t => t.entityRef.entityType === 'pipeline');
    application.getDataSource('pipelineConfigs').data.forEach((pipeline: IPipeline) => {
      pipeline.entityTags = pipelineTags.find(t => t.entityRef.entityId === pipeline.name);
    });
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
