import { IPromise, IQService, module } from 'angular';
import { flatten, forOwn, get, groupBy, has, head, keys, values } from 'lodash';

import { ArtifactReferenceService } from 'core/artifact';
import { API } from 'core/api';
import { Application } from 'core/application';
import { NameUtils } from 'core/naming';
import { FilterModelService } from 'core/filterModel';
import { IArtifactExtractor, ICluster, IClusterSummary, IExecution, IExecutionStage, IServerGroup } from 'core/domain';
import { ClusterState } from 'core/state';
import { ProviderServiceDelegate } from 'core/cloudProvider';

import { taskMatcher } from './task.matcher';

export class ClusterService {
  public static ON_DEMAND_THRESHOLD = 350;

  constructor(
    private $q: IQService,
    private serverGroupTransformer: any,
    private providerServiceDelegate: ProviderServiceDelegate,
  ) {
    'ngInject';
  }

  // Retrieves and normalizes all server groups. If a server group for an unsupported cloud provider (i.e. one that does
  // not have a server group transformer) is encountered, it will be omitted from the result.
  public loadServerGroups(application: Application): IPromise<IServerGroup[]> {
    return this.getClusters(application.name).then((clusters: IClusterSummary[]) => {
      const dataSource = application.getDataSource('serverGroups');
      const serverGroupLoader = API.one('applications')
        .one(application.name)
        .all('serverGroups');
      dataSource.fetchOnDemand = clusters.length > ClusterService.ON_DEMAND_THRESHOLD;
      if (dataSource.fetchOnDemand) {
        dataSource.clusters = clusters;
        serverGroupLoader.withParams({
          clusters: FilterModelService.getCheckValues(
            ClusterState.filterModel.asFilterModel.sortFilter.clusters,
          ).join(),
        });
      } else {
        this.reconcileClusterDeepLink();
      }
      return serverGroupLoader.getList().then((serverGroups: IServerGroup[]) => {
        serverGroups.forEach(sg => this.addHealthStatusCheck(sg));
        serverGroups.forEach(sg => this.addNameParts(sg));
        return this.$q
          .all(serverGroups.map(sg => this.serverGroupTransformer.normalizeServerGroup(sg, application)))
          .then(normalized => normalized.filter(Boolean));
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
      selectedClusters.forEach(clusterKey => {
        const [account, cluster] = clusterKey.split(':');
        accountNames.push(account);
        if (cluster) {
          clusterNames.push(cluster);
        }
      });
      if (clusterNames.length) {
        accountNames.forEach(account => (ClusterState.filterModel.asFilterModel.sortFilter.account[account] = true));
        ClusterState.filterModel.asFilterModel.sortFilter.filter = `clusters:${clusterNames.join()}`;
        ClusterState.filterModel.asFilterModel.sortFilter.clusters = {};
        ClusterState.filterModel.asFilterModel.applyParamsToUrl();
      }
    }
  }

  public addServerGroupsToApplication(application: Application, serverGroups: IServerGroup[] = []): IServerGroup[] {
    if (application.serverGroups.data) {
      const data = application.serverGroups.data;
      // remove any that have dropped off, update any that have changed
      const toRemove: number[] = [];
      data.forEach((serverGroup: IServerGroup, idx: number) => {
        const matches = serverGroups.filter(
          test =>
            test.name === serverGroup.name &&
            test.account === serverGroup.account &&
            test.region === serverGroup.region &&
            test.category === serverGroup.category,
        );
        if (!matches.length) {
          toRemove.push(idx);
        } else {
          if (serverGroup.stringVal && matches[0].stringVal && serverGroup.stringVal !== matches[0].stringVal) {
            data[idx] = matches[0];
          }
        }
      });

      toRemove.forEach(idx => data.splice(idx, 1));

      // add any new ones
      serverGroups.forEach(serverGroup => {
        if (
          !application.serverGroups.data.filter(
            (test: IServerGroup) =>
              test.name === serverGroup.name &&
              test.account === serverGroup.account &&
              test.region === serverGroup.region &&
              test.category === serverGroup.category,
          ).length
        ) {
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
    const executions = get(application, 'runningExecutions.data', []);

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
            (stageRegion === serverGroup.region || stageRegion === serverGroup.namespace)
          ) {
            serverGroup.runningExecutions.push(execution);
          }
        });
      });
    });
  }

  public addTasksToServerGroups(application: Application): void {
    const runningTasks = get(application, 'runningTasks.data', []);
    if (!application.serverGroups.data) {
      return; // still run if there are no running tasks, since they may have all finished and we need to clear them.
    }
    application.serverGroups.data.forEach((serverGroup: IServerGroup) => {
      if (!serverGroup.runningTasks) {
        serverGroup.runningTasks = [];
      } else {
        serverGroup.runningTasks.length = 0;
      }
      runningTasks.forEach(function(task) {
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

  private getClusters(application: string): IPromise<IClusterSummary[]> {
    return API.one('applications')
      .one(application)
      .one('clusters')
      .get()
      .then((clustersMap: { [account: string]: string[] }) => {
        const clusters: IClusterSummary[] = [];
        Object.keys(clustersMap).forEach(account => {
          clustersMap[account].forEach(name => {
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
      stage =>
        (['createServerGroup', 'deploy', 'destroyAsg', 'resizeAsg'].includes(stage.type) &&
          has(stage.context, 'deploy.server.groups')) ||
        (stage.type === 'disableAsg' && has(stage.context, 'targetop.asg.disableAsg.name')) ||
        has(stage.context, 'outputs.manifestNamesByNamespace'),
    );
  }

  private addProvidersAndServerGroupsToInstances(serverGroups: IServerGroup[]) {
    serverGroups.forEach(serverGroup => {
      serverGroup.instances.forEach(instance => {
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
    serverGroup.instances.forEach(instance => {
      instance.hasHealthStatus = (instance.health || []).some(h => h.state !== 'Unknown');
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
    operand.forEach(serverGroup => {
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
module(CLUSTER_SERVICE, [require('../serverGroup/serverGroup.transformer.js').name]).service(
  'clusterService',
  ClusterService,
);
