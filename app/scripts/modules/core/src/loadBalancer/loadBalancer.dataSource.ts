import { module, IQService } from 'angular';

import { APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry } from 'core/application/service/applicationDataSource.registry';
import { Application } from 'core/application/application.model';
import { DataSourceConfig } from 'core/application/service/applicationDataSource';
import { ENTITY_TAGS_READ_SERVICE, EntityTagsReader } from 'core/entityTag/entityTags.read.service';
import { ILoadBalancer } from 'core/domain';
import { LOAD_BALANCER_READ_SERVICE, LoadBalancerReader } from 'core/loadBalancer/loadBalancer.read.service';
import { SETTINGS } from 'core/config/settings';

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
    return addTags(loadBalancers);
  };

  const addTags = (loadBalancers: ILoadBalancer[]) => {
    if (!SETTINGS.feature.entityTags) {
      return $q.when(loadBalancers);
    }
    const entityIds = loadBalancers.map(lb => lb.name);
    return entityTagsReader.getAllEntityTags('loadBalancer', entityIds).then(tags => {
      loadBalancers.forEach(loadBalancer => {
        loadBalancer.entityTags = tags.find(t => t.entityRef.entityId === loadBalancer.name &&
        t.entityRef.account === loadBalancer.account &&
        t.entityRef.region === loadBalancer.region);
      });
      return loadBalancers;
    });
  };

  applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
    key: 'loadBalancers',
    optional: true,
    loader: loadLoadBalancers,
    onLoad: addLoadBalancers,
    providerField: 'cloudProvider',
    credentialsField: 'account',
    regionField: 'region',
    description: 'Traffic distribution management between servers'
  }));
});
