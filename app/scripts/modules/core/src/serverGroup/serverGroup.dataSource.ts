import { module, IQService } from 'angular';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { INFRASTRUCTURE_KEY } from 'core/application/nav/defaultCategories';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import { CLUSTER_SERVICE, ClusterService } from 'core/cluster/cluster.service';
import { JsonUtils } from 'core/utils';
import { Application } from 'core/application/application.model';
import { IServerGroup } from 'core/domain';

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
        serverGroup =>
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
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'serverGroups',
      label: 'Clusters',
      category: INFRASTRUCTURE_KEY,
      sref: '.insight.clusters',
      optional: true,
      primary: true,
      icon: 'fas fa-xs fa-fw fa-th-large',
      loader: loadServerGroups,
      onLoad: addServerGroups,
      afterLoad: addTags,
      providerField: 'type',
      credentialsField: 'account',
      regionField: 'region',
      description: 'Collections of server groups or jobs',
    });
  },
]);
