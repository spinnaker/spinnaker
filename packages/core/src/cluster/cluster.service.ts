import { IQService, module } from 'angular';
import { flatten, forOwn, groupBy, has, head, keyBy, keys, values } from 'lodash';

import { REST } from '../api';
import { Application } from '../application';
import { ArtifactReferenceService } from '../artifact';
import { ProviderServiceDelegate } from '../cloudProvider';
import { SETTINGS } from '../config/settings';
import {
  IArtifactExtractor,
  ICluster,
  IClusterSummary,
  IExecution,
  IExecutionStage,
  IServerGroup,
  ITask,
} from '../domain';
import { FilterModelService } from '../filterModel';
import { NameUtils } from '../naming';
import { CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER } from '../serverGroup/serverGroup.transformer';
import { ClusterState } from '../state';
import { taskMatcher } from './task.matcher';

export class ClusterService {
  public static $inject = ['$q', 'serverGroupTransformer', 'providerServiceDelegate'];
  constructor(
    private $q: IQService,
    private serverGroupTransformer: any,
    private providerServiceDelegate: ProviderServiceDelegate,
  ) {}

  // Retrieves and normalizes all server groups. If a server group for an unsupported cloud provider (i.e. one that does
  // not have a server group transformer) is encountered, it will be omitted from the result.
  public loadServerGroups(application: Application): PromiseLike<IServerGroup[]> {
    return this.getClusters(application.name).then((clusters: IClusterSummary[]) => {
      const dataSource = application.getDataSource('serverGroups');
      let serverGroupLoader = REST('/applications').path(application.name, 'serverGroups');
      dataSource.fetchOnDemand = clusters.length > SETTINGS.onDemandClusterThreshold;
      if (dataSource.fetchOnDemand) {
        dataSource.clusters = clusters;
        serverGroupLoader = serverGroupLoader.query({
          clusters: FilterModelService.getCheckValues(
            ClusterState.filterModel.asFilterModel.sortFilter.clusters,
          ).join(),
        });
      } else {
        this.reconcileClusterDeepLink();
      }
      return serverGroupLoader.get().then((serverGroups: IServerGroup[]) => {
        serverGroups.forEach((sg) => this.addHealthStatusCheck(sg));
        serverGroups.forEach((sg) => this.addNameParts(sg));
        return this.$q
          .all(serverGroups.map((sg) => this.serverGroupTransformer.normalizeServerGroup(sg, application)))
          .then((normalized) => normalized.filter(Boolean));
      });
    });
  }

  // if the application is deep linked via "clusters:", but the app is not "fetchOnDemand" sized, convert the parameters
  // to the normal, filterable structure
  private reconcileClusterDeepLink() {
    const selectedClusters: string[] = FilterModelService.getCheckValues(
      ClusterState.filterModel.asFilterModel.sortFilter.clusters,
    );
    if (selectedClusters && selectedClusters.length) {
      const clusterNames: string[] = [];
      const accountNames: string[] = [];
      selectedClusters.forEach((clusterKey) => {
        const [account, cluster] = clusterKey.split(':');
        accountNames.push(account);
        if (cluster) {
          clusterNames.push(cluster);
        }
      });
      if (clusterNames.length) {
        accountNames.forEach((account) => (ClusterState.filterModel.asFilterModel.sortFilter.account[account] = true));
        ClusterState.filterModel.asFilterModel.sortFilter.filter = `clusters:${clusterNames.join()}`;
        ClusterState.filterModel.asFilterModel.sortFilter.clusters = {};
        ClusterState.filterModel.asFilterModel.applyParamsToUrl();
      }
    }
  }

  private generateServerGroupLookupKey(serverGroup: IServerGroup): string {
    const { name, account, region, category } = serverGroup;
    return [name, account, region, category].join('-');
  }

  public addServerGroupsToApplication(application: Application, serverGroups: IServerGroup[] = []): IServerGroup[] {
    // map of incoming data
    const remoteMap = keyBy(serverGroups, this.generateServerGroupLookupKey);
    // map local cache
    const localMap = keyBy(application.serverGroups.data, this.generateServerGroupLookupKey);

    if (application.serverGroups.data) {
      const data = application.serverGroups.data;
      // remove any that have dropped off, update any that have changed
      const toRemove: number[] = [];
      data.forEach((serverGroup: IServerGroup, idx: number) => {
        const match = remoteMap[this.generateServerGroupLookupKey(serverGroup)];
        if (match) {
          // Match found between local and incoming data, update but only if needed
          if (serverGroup.stringVal && match.stringVal && serverGroup.stringVal !== match.stringVal) {
            data[idx] = match;
          }
        } else {
          // Not found means server group was removed
          toRemove.push(idx);
        }
      });

      // IMPORTANT!!! - toRemove must be forEach'ed in decending order, so that we splice backwards.
      // For example, if we started with [0, 1, 2, 3, 4, 5] and wanted toRemove [0, 1],
      // Blindly forEach'ing and splicing like so: toRemove.forEach(idx => data.splice(idx, 1))
      // would result in the following at each step:
      // data              // [0, 1, 2, 3, 4, 5]
      // data.splice(0,1); // [1, 2, 3, 4, 5]
      // data.splice(1,1); // [1, 3, 4, 5]           wait, what??
      // If toRemove is in ascending order, every splice will cause everything to shift left
      // and every remaning index will no longer be correct (off by 1 for every iteration)
      // Works perfect in descending order though.
      toRemove
        // ensure indices are in descending order so splice can work properly
        .sort()
        .reverse()
        // splice is necessary to preserve referential equality
        .forEach((idx) => data.splice(idx, 1));

      // add any new ones
      serverGroups.forEach((serverGroup) => {
        const match = localMap[this.generateServerGroupLookupKey(serverGroup)];
        if (!match) {
          data.push(serverGroup);
        }
      });
      return data;
    } else {
      return serverGroups;
    }
  }

  public createServerGroupClusters(serverGroups: IServerGroup[]): ICluster[] {
    const clusters: ICluster[] = [];
    const groupedByAccount = groupBy(serverGroups, 'account');
    forOwn(groupedByAccount, (accountServerGroups, account) => {
      const groupedByCategory = groupBy(accountServerGroups, 'category');
      forOwn(groupedByCategory, (categoryServerGroups, category) => {
        const groupedByCluster = groupBy(categoryServerGroups, 'cluster');
        forOwn(groupedByCluster, (clusterServerGroups, clusterName) => {
          const cluster: ICluster = {
            account,
            category,
            name: clusterName,
            serverGroups: clusterServerGroups,
            cloudProvider: clusterServerGroups[0].cloudProvider,
          };
          this.addHealthCountsToCluster(cluster);
          clusters.push(cluster);
        });
      });
    });
    this.addProvidersAndServerGroupsToInstances(serverGroups);
    return clusters;
  }

  public addExecutionsToServerGroups(application: Application): void {
    const executions = application.runningExecutions?.data ?? [];

    if (!application.serverGroups.data) {
      return; // still run if there are no running tasks, since they may have all finished and we need to clear them.
    }

    application.serverGroups.data.forEach((serverGroup: IServerGroup) => {
      serverGroup.runningExecutions = [];
      executions.forEach((execution: IExecution) => {
        this.findStagesWithServerGroupInfo(execution.stages).forEach((stage: IExecutionStage) => {
          const stageServerGroup = stage ? this.extractServerGroupNameFromContext(stage.context) : '';
          const stageAccount = stage && stage.context ? stage.context.account || stage.context.credentials : '';
          const stageRegion = stage ? this.extractRegionFromContext(stage.context) : '';
          if (
            stageServerGroup.includes(serverGroup.name) &&
            stageAccount === serverGroup.account &&
            stageRegion === serverGroup.region
          ) {
            serverGroup.runningExecutions.push(execution);
          }
        });
      });
    });
  }

  public addTasksToServerGroups(application: Application): void {
    const runningTasks: ITask[] = application.runningTasks?.data ?? [];
    if (!application.serverGroups.data) {
      return; // still run if there are no running tasks, since they may have all finished and we need to clear them.
    }
    application.serverGroups.data.forEach((serverGroup: IServerGroup) => {
      if (!serverGroup.runningTasks) {
        serverGroup.runningTasks = [];
      } else {
        serverGroup.runningTasks.length = 0;
      }
      runningTasks.forEach((task) => {
        if (taskMatcher.taskMatches(task, serverGroup)) {
          serverGroup.runningTasks.push(task);
        }
      });
    });
  }

  public isDeployingArtifact(cluster: ICluster): boolean {
    return cluster.imageSource === 'artifact';
  }

  public defaultArtifactExtractor(): IArtifactExtractor {
    return {
      extractArtifacts: (cluster: ICluster) => (this.isDeployingArtifact(cluster) ? [cluster.imageArtifactId] : []),
      removeArtifact: (cluster: ICluster, artifactId: string) => {
        ArtifactReferenceService.removeArtifactFromField('imageArtifactId', cluster, artifactId);
      },
    };
  }

  public getArtifactExtractor(cloudProvider: string): IArtifactExtractor {
    return this.providerServiceDelegate.hasDelegate(cloudProvider, 'serverGroup.artifactExtractor')
      ? this.providerServiceDelegate.getDelegate<IArtifactExtractor>(cloudProvider, 'serverGroup.artifactExtractor')
      : this.defaultArtifactExtractor();
  }

  public extractArtifacts(cluster: ICluster): string[] {
    return this.getArtifactExtractor(cluster.cloudProvider).extractArtifacts(cluster);
  }

  public removeArtifact(cluster: ICluster, artifactId: string): void {
    this.getArtifactExtractor(cluster.cloudProvider).removeArtifact(cluster, artifactId);
  }

  private getClusters(application: string): PromiseLike<IClusterSummary[]> {
    return REST('/applications')
      .path(application, 'clusters')
      .get()
      .then((clustersMap: { [account: string]: string[] }) => {
        const clusters: IClusterSummary[] = [];
        Object.keys(clustersMap).forEach((account) => {
          clustersMap[account].forEach((name) => {
            clusters.push({ account, name });
          });
        });
        return clusters;
      });
  }

  private extractServerGroupNameFromContext(context: any): string {
    return (
      head(values(context['deploy.server.groups'])) ||
      context['targetop.asg.disableAsg.name'] ||
      flatten(values(context['outputs.manifestNamesByNamespace'])) ||
      ''
    );
  }

  public extractRegionFromContext(context: any): string {
    return (
      head(keys(context['deploy.server.groups'] as string)) ||
      head(context['targetop.asg.disableAsg.regions'] as string) ||
      head(keys(context['outputs.manifestNamesByNamespace'])) ||
      ''
    );
  }

  private findStagesWithServerGroupInfo(stages: IExecutionStage[]): IExecutionStage[] {
    return (stages || []).filter(
      (stage) =>
        (['createServerGroup', 'deploy', 'destroyAsg', 'resizeAsg'].includes(stage.type) &&
          has(stage.context, 'deploy.server.groups')) ||
        (stage.type === 'disableAsg' && has(stage.context, 'targetop.asg.disableAsg.name')) ||
        has(stage.context, 'outputs.manifestNamesByNamespace'),
    );
  }

  private addProvidersAndServerGroupsToInstances(serverGroups: IServerGroup[]) {
    serverGroups.forEach((serverGroup) => {
      serverGroup.instances.forEach((instance) => {
        instance.provider = serverGroup.type || serverGroup.provider;
        instance.serverGroup = instance.serverGroup || serverGroup.name;
        instance.vpcId = serverGroup.vpcId;
      });
    });
  }

  private addNameParts(serverGroup: IServerGroup): void {
    const nameParts = NameUtils.parseServerGroupName(serverGroup.name);
    if (serverGroup.moniker) {
      Object.assign(serverGroup, serverGroup.moniker);
    } else {
      serverGroup.app = nameParts.application;
      serverGroup.stack = nameParts.stack;
      serverGroup.detail = nameParts.freeFormDetails;
      serverGroup.cluster = nameParts.cluster;
    }
    serverGroup.category = 'serverGroup';
  }

  private addHealthStatusCheck(serverGroup: IServerGroup): void {
    serverGroup.instances.forEach((instance) => {
      instance.hasHealthStatus = (instance.health || []).some((h) => h.state !== 'Unknown');
    });
  }

  private addHealthCountsToCluster(cluster: ICluster): void {
    cluster.instanceCounts = {
      up: 0,
      down: 0,
      unknown: 0,
      starting: 0,
      outOfService: 0,
      succeeded: 0,
      failed: 0,
      total: 0,
    };
    const operand = cluster.serverGroups || [];
    operand.forEach((serverGroup) => {
      if (!serverGroup.instanceCounts) {
        return;
      }
      cluster.instanceCounts.total += serverGroup.instanceCounts.total || 0;
      cluster.instanceCounts.up += serverGroup.instanceCounts.up || 0;
      cluster.instanceCounts.down += serverGroup.instanceCounts.down || 0;
      cluster.instanceCounts.unknown += serverGroup.instanceCounts.unknown || 0;
      cluster.instanceCounts.starting += serverGroup.instanceCounts.starting || 0;
      cluster.instanceCounts.outOfService += serverGroup.instanceCounts.outOfService || 0;
      cluster.instanceCounts.succeeded += serverGroup.instanceCounts.succeeded || 0;
      cluster.instanceCounts.failed += serverGroup.instanceCounts.failed || 0;
    });
  }
}

export const CLUSTER_SERVICE = 'spinnaker.core.cluster.service';
module(CLUSTER_SERVICE, [CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER]).service('clusterService', ClusterService);
