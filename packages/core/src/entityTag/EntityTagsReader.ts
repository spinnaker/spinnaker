import { $q } from 'ngimport';

import { REST } from '../api/ApiService';
import { Application } from '../application/application.model';
import { SETTINGS } from '../config/settings';
import {
  IExecution,
  IFunction,
  ILoadBalancer,
  IPipeline,
  ISecurityGroup,
  IServerGroup,
  IServerGroupManager,
} from '../domain';

import { ICreationMetadataTag, IEntityTag, IEntityTags } from '../domain/IEntityTags';

export class EntityTagsReader {
  public static getAllEntityTagsForApplication(application: string): PromiseLike<IEntityTags[]> {
    return REST('/tags')
      .query({ maxResults: SETTINGS.entityTags.maxResults || 5000, application })
      .get()
      .then((allTags: IEntityTags[]) => this.flattenTagsAndAddMetadata(allTags));
  }

  public static addTagsToServerGroups(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags: IEntityTags[] = application.entityTags.data;
    const serverGroupTags = allTags.filter(({ entityRef }) => entityRef.entityType === 'servergroup');
    const clusterTags = allTags.filter(({ entityRef }) => entityRef.entityType === 'cluster');
    const serverGroups: IServerGroup[] = application.serverGroups.data;

    serverGroups.forEach((serverGroup) => {
      serverGroup.entityTags = serverGroupTags.find(
        ({ entityRef }) =>
          entityRef.entityId === serverGroup.name &&
          entityRef.account === serverGroup.account &&
          entityRef.region === serverGroup.region,
      );
      serverGroup.clusterEntityTags = clusterTags.filter(
        ({ entityRef }) =>
          entityRef.entityId === serverGroup.cluster &&
          (entityRef.account === '*' || entityRef.account === serverGroup.account) &&
          (entityRef.region === '*' || entityRef.region === serverGroup.region),
      );
    });
  }

  public static addTagsToServerGroupManagers(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags: IEntityTags[] = application.entityTags.data;
    const serverGroupManagerTags = allTags.filter(({ entityRef }) => entityRef.entityType === 'servergroupmanager');
    const serverGroupManagers: IServerGroupManager[] = application.serverGroupManagers.data;

    serverGroupManagers.forEach((serverGroupManager) => {
      serverGroupManager.entityTags = serverGroupManagerTags.find(
        ({ entityRef }) =>
          entityRef.entityId === serverGroupManager.name &&
          entityRef.account === serverGroupManager.account &&
          entityRef.region === serverGroupManager.region,
      );
    });
  }

  public static addTagsToLoadBalancers(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags: IEntityTags[] = application.entityTags.data;
    const loadBalancerTags = allTags.filter(({ entityRef }) => entityRef.entityType === 'loadbalancer');
    const loadBalancers: ILoadBalancer[] = application.loadBalancers.data;

    loadBalancers.forEach((loadBalancer) => {
      loadBalancer.entityTags = loadBalancerTags.find(
        ({ entityRef }) =>
          entityRef.entityId === loadBalancer.name &&
          entityRef.account === loadBalancer.account &&
          entityRef.region === loadBalancer.region,
      );
    });
  }

  public static addTagsToFunctions(application: Application): void {
    if (!SETTINGS.feature.entityTags || !SETTINGS.feature.functions) {
      return;
    }
    const allTags: IEntityTags[] = application.entityTags.data;
    const functionTags = allTags.filter(({ entityRef }) => entityRef.entityType === 'function');
    const functions: IFunction[] = application.functions.data;

    functions.forEach((fn) => {
      fn.entityTags = functionTags.find(
        ({ entityRef }) =>
          entityRef.entityId === fn.functionName && entityRef.account === fn.account && entityRef.region === fn.region,
      );
    });
  }
  public static addTagsToSecurityGroups(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags: IEntityTags[] = application.entityTags.data;
    const securityGroupTags = allTags.filter(({ entityRef }) => entityRef.entityType === 'securitygroup');
    const securityGroups: ISecurityGroup[] = application.securityGroups.data;

    securityGroups.forEach((securityGroup) => {
      securityGroup.entityTags = securityGroupTags.find(
        ({ entityRef }) =>
          entityRef.entityId === securityGroup.name &&
          entityRef.account === securityGroup.account &&
          entityRef.region === securityGroup.region,
      );
    });
  }

  public static addTagsToExecutions(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags: IEntityTags[] = application.entityTags.data;
    const executionTags = allTags.filter(({ entityRef }) => entityRef.entityType === 'execution');
    const executions: IExecution[] = application.executions.data;

    executions.forEach((execution) => {
      execution.entityTags = executionTags.find(({ entityRef }) => entityRef.entityId === execution.id);
    });
  }

  public static addTagsToPipelines(application: Application): void {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const allTags: IEntityTags[] = application.entityTags.data;
    const pipelineTags = allTags.filter(({ entityRef }) => entityRef.entityType === 'pipeline');
    const pipelineConfigs: IPipeline[] = application.pipelineConfigs.data;

    pipelineConfigs.forEach((pipeline) => {
      pipeline.entityTags = pipelineTags.find(({ entityRef }) => entityRef.entityId === pipeline.id);
    });
  }

  public static getEntityTagsForId(entityType: string, entityId: string): PromiseLike<IEntityTags[]> {
    if (!entityId) {
      return $q.when([]);
    }
    return REST('/tags')
      .query({
        entityType: entityType.toLowerCase(),
        entityId,
      })
      .get()
      .then((entityTagGroups: IEntityTags[]) => {
        return this.flattenTagsAndAddMetadata(entityTagGroups);
      })
      .catch(() => {
        return $q.when([]);
      });
  }

  private static flattenTagsAndAddMetadata(entityTags: IEntityTags[]): IEntityTags[] {
    const allTags: IEntityTags[] = [];
    entityTags.forEach((entityTag) => {
      entityTag.tags.forEach((tag) => this.addTagMetadata(entityTag, tag));
      entityTag.alerts = entityTag.tags.filter((t) => t.name.startsWith('spinnaker_ui_alert:'));
      entityTag.notices = entityTag.tags.filter((t) => t.name.startsWith('spinnaker_ui_notice:'));
      entityTag.creationMetadata = entityTag.tags.find((t) => t.name === 'spinnaker:metadata') as ICreationMetadataTag;
      allTags.push(entityTag);
    });
    return allTags;
  }

  private static addTagMetadata(entityTag: IEntityTags, tag: IEntityTag): void {
    const metadata = entityTag.tagsMetadata.find((m) => m.name === tag.name);
    if (metadata) {
      tag.created = metadata.created;
      tag.createdBy = metadata.createdBy;
      tag.lastModified = metadata.lastModified;
      tag.lastModifiedBy = metadata.lastModifiedBy;
    }
  }
}
