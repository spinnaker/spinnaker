import type { Application } from '../application/application.model';
import { INFRASTRUCTURE_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { ClusterService } from '../cluster/cluster.service';
import type { IServerGroup } from '../domain';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import { addManagedResourceMetadataToServerGroups } from '../managed';
import { JsonUtils } from '../utils';

function createDataSourceConfig(clusterService: ClusterService) {
  const loadServerGroups = (application: Application) => {
    return clusterService.loadServerGroups(application);
  };

  const addServerGroups = (application: Application, serverGroups: IServerGroup[]) => {
    serverGroups.forEach(
      (serverGroup) =>
        (serverGroup.stringVal = JsonUtils.makeSortedString(serverGroup, ['executions', 'runningTasks', '$$hashKey'])),
    );
    application.clusters = clusterService.createServerGroupClusters(serverGroups);
    const data = clusterService.addServerGroupsToApplication(application, serverGroups);
    clusterService.addTasksToServerGroups(application);
    clusterService.addExecutionsToServerGroups(application);
    return Promise.resolve(data);
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

export function registerServerGroupDataSource(clusterService: ClusterService): void {
  if (ApplicationDataSourceRegistry.getDataSources().some((source) => source.key === 'serverGroups')) {
    return;
  }

  const dataSourceConfig = createDataSourceConfig(clusterService);
  ApplicationDataSourceRegistry.registerDataSource(dataSourceConfig);
}
