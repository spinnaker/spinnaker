import {DataSourceConfig} from '../application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from '../application/service/applicationDataSource.registry';
import {ENTITY_TAGS_READ_SERVICE} from '../entityTag/entityTags.read.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY,
    ENTITY_TAGS_READ_SERVICE,
    require('../cluster/cluster.service'),
    require('core/config/settings'),
  ])
  .run(function($q, applicationDataSourceRegistry, clusterService, entityTagsReader, serverGroupTransformer, settings) {

    let loadServerGroups = (application) => {
      return clusterService.loadServerGroups(application.name);
    };

    let addServerGroups = (application, serverGroups) => {
      return addTags(serverGroups).then(() => {
        serverGroups.forEach(serverGroup => serverGroup.stringVal = JSON.stringify(serverGroup, serverGroupTransformer.jsonReplacer));
        application.clusters = clusterService.createServerGroupClusters(serverGroups);
        let data = clusterService.addServerGroupsToApplication(application, serverGroups);
        clusterService.addTasksToServerGroups(application);
        clusterService.addExecutionsToServerGroups(application);
        return data;
      });
    };

    let addTags = (serverGroups) => {
      if (!settings.feature.entityTags) {
        return $q.when(null);
      }
      const entityIds = serverGroups.map(g => g.name);
      return entityTagsReader.getAllEntityTags('serverGroup', entityIds).then(tags => {
        serverGroups.forEach(serverGroup => {
          serverGroup.entityTags = tags.find(t => t.entityRef.entityId === serverGroup.name &&
            t.entityRef.account === serverGroup.account &&
            t.entityRef.region === serverGroup.region);
        });
      });
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
      description: 'Collections of server groups or jobs'
    }));
  });
