import { module, IQService } from 'angular';

import { APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry } from 'core/application/service/applicationDataSource.registry';
import { Application } from 'core/application/application.model';
import { ENTITY_TAGS_READ_SERVICE, EntityTagsReader } from './entityTags.read.service';
import { IEntityTags } from 'core/domain/IEntityTags';
import { noop } from 'core/utils';
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
    application.getDataSource('serverGroups').ready().then(() => entityTagsReader.addTagsToServerGroups(application), noop);
    application.getDataSource('loadBalancers').ready().then(() => entityTagsReader.addTagsToLoadBalancers(application), noop);
    application.getDataSource('securityGroups').ready().then(() => entityTagsReader.addTagsToSecurityGroups(application), noop);
  };

  applicationDataSourceRegistry.registerDataSource({
    key: 'entityTags',
    visible: false,
    loader: loadEntityTags,
    onLoad: addEntityTags,
    afterLoad: addTagsToEntities,
  });
});
