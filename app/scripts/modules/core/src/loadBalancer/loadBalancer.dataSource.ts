import { module, IQService } from 'angular';

import { APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry } from 'core/application/service/applicationDataSource.registry';
import { INFRASTRUCTURE_KEY } from 'core/application/nav/defaultCategories';
import { Application } from 'core/application/application.model';
import { ENTITY_TAGS_READ_SERVICE, EntityTagsReader } from 'core/entityTag/entityTags.read.service';
import { ILoadBalancer } from 'core/domain';
import { LOAD_BALANCER_READ_SERVICE, LoadBalancerReader } from 'core/loadBalancer/loadBalancer.read.service';

export const LOAD_BALANCER_DATA_SOURCE = 'spinnaker.core.loadBalancer.dataSource';
module(LOAD_BALANCER_DATA_SOURCE, [
    APPLICATION_DATA_SOURCE_REGISTRY,
    ENTITY_TAGS_READ_SERVICE,
    LOAD_BALANCER_READ_SERVICE
]).run(($q: IQService, applicationDataSourceRegistry: ApplicationDataSourceRegistry, loadBalancerReader: LoadBalancerReader, entityTagsReader: EntityTagsReader) => {
  const loadLoadBalancers = (application: Application) => {
    return loadBalancerReader.loadLoadBalancers(application.name);
  };

  const addLoadBalancers = (_application: Application, loadBalancers: ILoadBalancer[]) => {
    return $q.when(loadBalancers);
  };

  const addTags = (application: Application) => {
    entityTagsReader.addTagsToLoadBalancers(application);
  };

  applicationDataSourceRegistry.registerDataSource({
    key: 'loadBalancers',
    category: INFRASTRUCTURE_KEY,
    optional: true,
    icon: 'fa fa-xs fa-fw icon-sitemap',
    loader: loadLoadBalancers,
    onLoad: addLoadBalancers,
    afterLoad: addTags,
    providerField: 'cloudProvider',
    credentialsField: 'account',
    regionField: 'region',
    description: 'Traffic distribution management between servers'
  });
});
