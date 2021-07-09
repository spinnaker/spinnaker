import { IQService, module } from 'angular';

import { EntityTagsReader } from './EntityTagsReader';
import { ApplicationDataSourceRegistry } from '../application';
import { Application } from '../application/application.model';
import { SETTINGS } from '../config/settings';
import { IEntityTags } from '../domain/IEntityTags';
import { LOAD_BALANCER_READ_SERVICE } from '../loadBalancer/loadBalancer.read.service';
import { noop } from '../utils';

export const ENTITY_TAGS_DATA_SOURCE = 'spinnaker.core.entityTag.dataSource';
module(ENTITY_TAGS_DATA_SOURCE, [LOAD_BALANCER_READ_SERVICE]).run([
  '$q',
  ($q: IQService) => {
    if (!SETTINGS.feature.entityTags) {
      return;
    }
    const loadEntityTags = (application: Application) => {
      return EntityTagsReader.getAllEntityTagsForApplication(application.name);
    };

    const addEntityTags = (_application: Application, data: IEntityTags[]) => {
      return $q.when(data);
    };

    const addTagsToEntities = (application: Application) => {
      application
        .getDataSource('serverGroups')
        .ready()
        .then(() => EntityTagsReader.addTagsToServerGroups(application), noop);
      application
        .getDataSource('serverGroupManagers')
        .ready()
        .then(() => EntityTagsReader.addTagsToServerGroupManagers(application), noop);
      application
        .getDataSource('loadBalancers')
        .ready()
        .then(() => EntityTagsReader.addTagsToLoadBalancers(application), noop);
      application
        .getDataSource('securityGroups')
        .ready()
        .then(() => EntityTagsReader.addTagsToSecurityGroups(application), noop);
      application
        .getDataSource('executions')
        .ready()
        .then(() => EntityTagsReader.addTagsToExecutions(application), noop);
      application
        .getDataSource('pipelineConfigs')
        .ready()
        .then(() => EntityTagsReader.addTagsToPipelines(application), noop);
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'entityTags',
      visible: false,
      loader: loadEntityTags,
      onLoad: addEntityTags,
      afterLoad: addTagsToEntities,
      defaultData: [],
    });
  },
]);
