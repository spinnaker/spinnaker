import {DataSourceConfig} from '../application/service/applicationDataSource.ts';
import dataSourceRegistryModule from '../application/service/applicationDataSource.registry.ts';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup.dataSource', [
    dataSourceRegistryModule,
    require('../cluster/cluster.service'),
  ])
  .run(function($q, applicationDataSourceRegistry, clusterService, serverGroupTransformer) {

    let loadServerGroups = (application) => {
      return clusterService.loadServerGroups(application.name);
    };

    let addServerGroups = (application, serverGroups) => {
      serverGroups.forEach(serverGroup => serverGroup.stringVal = JSON.stringify(serverGroup, serverGroupTransformer.jsonReplacer));
      application.clusters = clusterService.createServerGroupClusters(serverGroups);
      let data = clusterService.addServerGroupsToApplication(application, serverGroups);
      clusterService.addTasksToServerGroups(application);
      clusterService.addExecutionsToServerGroups(application);
      return $q.when(data);
    };

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'serverGroups',
      label: 'Clusters',
      sref: '.insight.clusters',
      optional: true,
      loader: loadServerGroups,
      onLoad: addServerGroups,
      providerField: 'type',
      credentialsField: 'account',
      regionField: 'region',
    }));
  });
