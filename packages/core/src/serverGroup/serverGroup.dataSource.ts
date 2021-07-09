import { IQService, module } from 'angular';

import { Application } from '../application/application.model';
import { INFRASTRUCTURE_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { CLUSTER_SERVICE, ClusterService } from '../cluster/cluster.service';
import { IServerGroup } from '../domain';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import { addManagedResourceMetadataToServerGroups } from '../managed';
import { JsonUtils } from '../utils';

export const SERVER_GROUP_DATA_SOURCE = 'spinnaker.core.serverGroup.dataSource';

module(SERVER_GROUP_DATA_SOURCE, [CLUSTER_SERVICE]).run([
  '$q',
  'clusterService',
  ($q: IQService, clusterService: ClusterService) => {
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
      return $q.when(data);
    };

    const addTags = (application: Application) => {
      EntityTagsReader.addTagsToServerGroups(application);
      addManagedResourceMetadataToServerGroups(application);
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'serverGroups',
      label: 'Clusters',
      category: INFRASTRUCTURE_KEY,
      sref: '.insight.clusters',
      optional: true,
      primary: true,
      icon: 'fas fa-xs fa-fw fa-th-large',
      iconName: 'spMenuClusters',
      loader: loadServerGroups,
      onLoad: addServerGroups,
      afterLoad: addTags,
      providerField: 'type',
      credentialsField: 'account',
      regionField: 'region',
      description: 'Collections of server groups or jobs',
      defaultData: [],
    });
  },
]);
