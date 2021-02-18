import { IQService, module } from 'angular';
import { ApplicationDataSourceRegistry } from 'core/application';
import { Application } from 'core/application/application.model';
import { SETTINGS } from 'core/config/settings';
import { IEntityTags } from 'core/domain/IEntityTags';
import { LOAD_BALANCER_READ_SERVICE } from 'core/loadBalancer/loadBalancer.read.service';
import { noop } from 'core/utils';

import { EntityTagsReader } from './EntityTagsReader';

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
