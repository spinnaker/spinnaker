import {uniq} from 'lodash';
let angular = require('angular');

import {DataSourceConfig} from '../application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from '../application/service/applicationDataSource.registry';
import {ENTITY_TAGS_READ_SERVICE} from '../entityTag/entityTags.read.service';
import {SETTINGS} from 'core/config/settings';
import {CLUSTER_SERVICE} from 'core/cluster/cluster.service';
import {JSON_UTILITY_SERVICE} from 'core/utils/json/json.utility.service';

module.exports = angular
  .module('spinnaker.core.serverGroup.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY,
    ENTITY_TAGS_READ_SERVICE,
    CLUSTER_SERVICE,
    JSON_UTILITY_SERVICE,
  ])
  .run(function($q, applicationDataSourceRegistry, clusterService, entityTagsReader, serverGroupTransformer, jsonUtilityService) {

    let loadServerGroups = (application) => {
      return clusterService.loadServerGroups(application);
    };

    let addServerGroups = (application, serverGroups) => {
      return addTags(serverGroups).then(() => {
        serverGroups.forEach(serverGroup => serverGroup.stringVal =
          jsonUtilityService.makeSortedStringFromAngularObject(serverGroup, ['executions', 'runningTasks']));
        application.clusters = clusterService.createServerGroupClusters(serverGroups);
        let data = clusterService.addServerGroupsToApplication(application, serverGroups);
        clusterService.addTasksToServerGroups(application);
        clusterService.addExecutionsToServerGroups(application);
        return data;
      });
    };

    let addTags = (serverGroups) => {
      if (!SETTINGS.feature.entityTags) {
        return $q.when(null);
      }
      const serverGroupNames = uniq(serverGroups.map(g => g.name));
      const clusterNames = uniq(serverGroups.map(g => g.cluster));
      const serverGroupTagger = entityTagsReader.getAllEntityTags('serverGroup', serverGroupNames).then(tags => {
        serverGroups.forEach(serverGroup => {
          serverGroup.entityTags = tags.find(t => t.entityRef.entityId === serverGroup.name &&
            t.entityRef.account === serverGroup.account &&
            t.entityRef.region === serverGroup.region);
        });
      });
      const clusterTagger = entityTagsReader.getAllEntityTags('cluster', clusterNames).then(tags => {
        serverGroups.forEach(serverGroup => {
          serverGroup.clusterEntityTags = tags.filter(t => t.entityRef.entityId === serverGroup.cluster &&
            t.entityRef.account === serverGroup.account &&
            (t.entityRef.region === '*' || t.entityRef.region === serverGroup.region)
          );
        });
      });
      return $q.all([serverGroupTagger, clusterTagger]);
    };

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'serverGroups',
      label: 'Clusters',
      sref: '.insight.clusters',
      optional: true,
      primary: true,
      icon: 'th-large',
      loader: loadServerGroups,
      onLoad: addServerGroups,
      providerField: 'type',
      credentialsField: 'account',
      regionField: 'region',
      description: 'Collections of server groups or jobs'
    }));
  });
