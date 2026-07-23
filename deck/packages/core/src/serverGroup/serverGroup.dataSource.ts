import type { IQService } from 'angular';
import { module } from 'angular';

import type { Application } from '../application/application.model';
import { INFRASTRUCTURE_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { ClusterService } from '../cluster/cluster.service';
import { CLUSTER_SERVICE } from '../cluster/cluster.service';
import type { IServerGroup } from '../domain';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import { addManagedResourceMetadataToServerGroups } from '../managed';
import { JsonUtils } from '../utils';

export const SERVER_GROUP_DATA_SOURCE = 'spinnaker.core.serverGroup.dataSource';

function createDataSourceConfig(
  clusterService: ClusterService,
  when: <T>(value: T | PromiseLike<T>) => PromiseLike<T>,
) {
  const loadServerGroups = (application: Application) => {
    return clusterService.loadServerGroups(application);
  };

  const addServerGroups = (application: Application, serverGroups: IServerGroup[]) => {
    serverGroups.forEach(
      (serverGroup) =>
        (serverGroup.stringVal = JsonUtils.makeSortedStringFromAngularObject(serverGroup, [
          'executions',
          'runningTasks',
        ])),
    );
    application.clusters = clusterService.createServerGroupClusters(serverGroups);
    const data = clusterService.addServerGroupsToApplication(application, serverGroups);
    clusterService.addTasksToServerGroups(application);
    clusterService.addExecutionsToServerGroups(application);
    return when(data);
  };

  const addTags = (application: Application) => {
    EntityTagsReader.addTagsToServerGroups(application);
    addManagedResourceMetadataToServerGroups(application);
  };

  return {
    key: 'serverGroups',
    label: 'Clusters',
    category: INFRASTRUCTURE_KEY,
    sref: '.insight.clusters',
    optional: true,
    primary: true,
    icon: 'fas fa-xs fa-fw fa-th-large',
    iconName: 'spMenuClusters' as const,
    loader: loadServerGroups,
    onLoad: addServerGroups,
    afterLoad: addTags,
    providerField: 'type',
    credentialsField: 'account',
    regionField: 'region',
    description: 'Collections of server groups or jobs',
    defaultData: [] as IServerGroup[],
  };
}

export function registerServerGroupDataSource($q: IQService, clusterService: ClusterService): void {
  if (ApplicationDataSourceRegistry.getDataSources().some((source) => source.key === 'serverGroups')) {
    return;
  }

  const dataSourceConfig = createDataSourceConfig(
    clusterService,
    $q ? <T>(value: T | PromiseLike<T>) => $q.when(value) : <T>(value: T | PromiseLike<T>) => Promise.resolve(value),
  );
  ApplicationDataSourceRegistry.registerDataSource(dataSourceConfig);
}

module(SERVER_GROUP_DATA_SOURCE, [CLUSTER_SERVICE]).run([
  '$q',
  'clusterService',
  ($q: IQService, clusterService: ClusterService) => registerServerGroupDataSource($q, clusterService),
]);
