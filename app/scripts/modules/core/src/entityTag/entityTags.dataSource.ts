import { module, IQService } from 'angular';
import { IEntityTags } from 'core';

import { APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry } from 'core/application/service/applicationDataSource.registry';
import { Application } from 'core/application/application.model';
import { ENTITY_TAGS_READ_SERVICE, EntityTagsReader } from 'core/entityTag/entityTags.read.service';
import { LOAD_BALANCER_READ_SERVICE } from 'core/loadBalancer/loadBalancer.read.service';
import { SETTINGS } from 'core/config/settings';

export const ENTITY_TAGS_DATA_SOURCE = 'spinnaker.core.entityTag.dataSource';
module(ENTITY_TAGS_DATA_SOURCE, [
  APPLICATION_DATA_SOURCE_REGISTRY,
  ENTITY_TAGS_READ_SERVICE,
  LOAD_BALANCER_READ_SERVICE
]).run(($q: IQService, applicationDataSourceRegistry: ApplicationDataSourceRegistry, entityTagsReader: EntityTagsReader) => {
  if (!SETTINGS.feature.entityTags) {
    return;
  }
  const loadEntityTags = (application: Application) => {
    return entityTagsReader.getAllEntityTagsForApplication(application.name);
  };

  const addEntityTags = (_application: Application, data: IEntityTags[]) => {
    return $q.when(data);
  };

  const addTagsToEntities = (application: Application) => {
    application.getDataSource('serverGroups').ready().then(() => entityTagsReader.addTagsToServerGroups(application));
    application.getDataSource('loadBalancers').ready().then(() => entityTagsReader.addTagsToLoadBalancers(application));
    application.getDataSource('securityGroups').ready().then(() => entityTagsReader.addTagsToSecurityGroups(application));
  };

  applicationDataSourceRegistry.registerDataSource({
    key: 'entityTags',
    visible: false,
    loader: loadEntityTags,
    onLoad: addEntityTags,
    afterLoad: addTagsToEntities,
  });
});
