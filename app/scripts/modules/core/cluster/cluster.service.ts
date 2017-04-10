import {IPromise, IQService, module} from 'angular';
import {forOwn, get, groupBy, has, head, keys, values} from 'lodash';

import {Api, API_SERVICE} from '../api/api.service';
import {NAMING_SERVICE, NamingService} from 'core/naming/naming.service';
import {taskMatches} from './task.matcher';
import {ServerGroup} from '../domain/serverGroup';
import {Application} from '../application/application.model';
import {ICluster, IClusterSummary} from '../domain/ICluster';
import {IExecutionStage} from '../domain/IExecutionStage';
import {IExecution} from '../domain/IExecution';

export class ClusterService {

  static get $inject() { return ['$q', 'API', 'serverGroupTransformer', 'namingService', 'ClusterFilterModel', 'filterModelService']; }
  constructor(private $q: IQService,
              private API: Api,
              private serverGroupTransformer: any,
              private namingService: NamingService,
              private ClusterFilterModel: any,
              private filterModelService: any) {}

  public loadServerGroups(application: Application): IPromise<ServerGroup[]> {
    return this.getClusters(application.name).then((clusters: IClusterSummary[]) => {
      const dataSource = application.getDataSource('serverGroups');
      const serverGroupLoader = this.API.one('applications').one(application.name).all('serverGroups');
      dataSource.fetchOnDemand = clusters.length > 250;
      if (dataSource.fetchOnDemand) {
        dataSource.clusters = clusters;
        serverGroupLoader.withParams({
          clusters: this.filterModelService.getCheckValues(this.ClusterFilterModel.sortFilter.clusters).join()
        });
      }
      return serverGroupLoader.getList().then((serverGroups: ServerGroup[]) => {
        serverGroups.forEach(sg => this.addHealthStatusCheck(sg));
        serverGroups.forEach(sg => this.addNameParts(sg));
        return this.$q.all(serverGroups.map(sg => this.serverGroupTransformer.normalizeServerGroup(sg, application)));
      });
    });
  }

  public addServerGroupsToApplication(application: Application, serverGroups: ServerGroup[] = []): ServerGroup[] {
    if (application.serverGroups.data) {
      let data = application.serverGroups.data;
      // remove any that have dropped off, update any that have changed
      let toRemove: number[] = [];
      data.forEach((serverGroup: ServerGroup, idx: number) => {
        let matches = serverGroups.filter((test) =>
          test.name === serverGroup.name &&
          test.account === serverGroup.account &&
          test.region === serverGroup.region &&
          test.category === serverGroup.category
        );
        if (!matches.length) {
          toRemove.push(idx);
        } else {
          if (serverGroup.stringVal && matches[0].stringVal && serverGroup.stringVal !== matches[0].stringVal) {
            data[idx] = matches[0];
          }
        }
      });

      toRemove.forEach((idx) => data.splice(idx, 1));

      // add any new ones
      serverGroups.forEach((serverGroup) => {
        if (!application.serverGroups.data.filter((test: ServerGroup) =>
            test.name === serverGroup.name &&
            test.account === serverGroup.account &&
            test.region === serverGroup.region &&
            test.category === serverGroup.category
          ).length) {
          data.push(serverGroup);
        }
      });
      return data;
    } else {
      return serverGroups;
    }
  }

  public createServerGroupClusters(serverGroups: ServerGroup[]): ICluster[] {
    const clusters: ICluster[] = [];
    const groupedByAccount = groupBy(serverGroups, 'account');
    forOwn(groupedByAccount, (accountServerGroups, account) => {
      const groupedByCategory = groupBy(accountServerGroups, 'category');
      forOwn(groupedByCategory, (categoryServerGroups, category) => {
        const groupedByCluster = groupBy(categoryServerGroups, 'cluster');
        forOwn(groupedByCluster, (clusterServerGroups, clusterName) => {
          const cluster: ICluster = {
            account: account,
            category: category,
            name: clusterName,
            serverGroups: clusterServerGroups,
            cloudProvider: clusterServerGroups[0].cloudProvider
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

    application.serverGroups.data.forEach((serverGroup: ServerGroup) => {
      serverGroup.runningExecutions = [];
      executions.forEach((execution: IExecution) => {
        this.findStagesWithServerGroupInfo(execution.stages).forEach((stage: IExecutionStage) => {
          const stageServerGroup = stage ? this.extractServerGroupNameFromContext(stage.context) : '';
          const stageAccount = stage && stage.context ? stage.context.account || stage.context.credentials : '';
          const stageRegion = stage ? this.extractRegionFromContext(stage.context) : '';

          if (stageServerGroup.includes(serverGroup.name) &&
            stageAccount === serverGroup.account &&
            stageRegion === serverGroup.region) {
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
    application.serverGroups.data.forEach((serverGroup: ServerGroup) => {
      if (!serverGroup.runningTasks) {
        serverGroup.runningTasks = [];
      } else {
        serverGroup.runningTasks.length = 0;
      }
      runningTasks.forEach(function(task) {
        if (taskMatches(task, serverGroup)) {
          serverGroup.runningTasks.push(task);
        }
      });
    });
  }

  private getClusters(application: string): IPromise<IClusterSummary[]> {
    return this.API.one('applications').one(application).one('clusters').get().then((clustersMap: {[account: string]: string[]}) => {
      const clusters: IClusterSummary[] = [];
      Object.keys(clustersMap).forEach(account => {
        clustersMap[account].forEach(name => {
          clusters.push({account, name});
        });
      });
      return clusters;
    });
  }

  private extractServerGroupNameFromContext(context: any): string {
    return head(values(context['deploy.server.groups'])) || context['targetop.asg.disableAsg.name'] || '';
  }

  public extractRegionFromContext(context: any): string {
    return head(keys(context['deploy.server.groups'] as string)) || head(context['targetop.asg.disableAsg.regions'] as string) || '';
  }

  private findStagesWithServerGroupInfo(stages: IExecutionStage[]): IExecutionStage[] {
    return (stages || []).filter(stage =>
      (['deploy', 'destroyAsg', 'resizeAsg'].includes(stage.type) && has(stage.context, 'deploy.server.groups')) ||
        (stage.type === 'disableAsg' && has(stage.context, 'targetop.asg.disableAsg.name'))
    );
  }

  private addProvidersAndServerGroupsToInstances(serverGroups: ServerGroup[]) {
    serverGroups.forEach((serverGroup) => {
      serverGroup.instances.forEach((instance) => {
        instance.provider = serverGroup.type || serverGroup.provider;
        instance.serverGroup = instance.serverGroup || serverGroup.name;
        instance.vpcId = serverGroup.vpcId;
      });
    });
  }

  private addNameParts(serverGroup: ServerGroup): void {
    const nameParts = this.namingService.parseServerGroupName(serverGroup.name);
    serverGroup.app = nameParts.application;
    serverGroup.stack = nameParts.stack;
    serverGroup.detail = nameParts.freeFormDetails;
    serverGroup.cluster = nameParts.cluster;
    serverGroup.category = 'serverGroup';
  }

  private addHealthStatusCheck(serverGroup: ServerGroup): void {
    serverGroup.instances.forEach((instance) => {
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
module(CLUSTER_SERVICE, [
  API_SERVICE,
  NAMING_SERVICE,
  require('./filter/clusterFilter.model'),
  require('../serverGroup/serverGroup.transformer.js')
]).service('clusterService', ClusterService);
