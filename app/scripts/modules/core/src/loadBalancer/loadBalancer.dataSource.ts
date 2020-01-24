import { module, IQService } from 'angular';

import { ApplicationDataSourceRegistry } from 'core/application/service/ApplicationDataSourceRegistry';
import { INFRASTRUCTURE_KEY } from 'core/application/nav/defaultCategories';
import { Application } from 'core/application/application.model';
import { EntityTagsReader } from 'core/entityTag/EntityTagsReader';
import { ILoadBalancer } from 'core/domain';
import { LOAD_BALANCER_READ_SERVICE, LoadBalancerReader } from './loadBalancer.read.service';
import { addManagedResourceMetadataToLoadBalancers } from 'core/managed';

export const LOAD_BALANCER_DATA_SOURCE = 'spinnaker.core.loadBalancer.dataSource';
module(LOAD_BALANCER_DATA_SOURCE, [LOAD_BALANCER_READ_SERVICE]).run([
  '$q',
  'loadBalancerReader',
  ($q: IQService, loadBalancerReader: LoadBalancerReader) => {
    const loadLoadBalancers = (application: Application) => {
      return loadBalancerReader.loadLoadBalancers(application.name);
    };

    const addLoadBalancers = (_application: Application, loadBalancers: ILoadBalancer[]) => {
      return $q.when(loadBalancers);
    };

    const addTags = (application: Application) => {
      EntityTagsReader.addTagsToLoadBalancers(application);
      addManagedResourceMetadataToLoadBalancers(application);
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'loadBalancers',
      sref: '.insight.loadBalancers',
      category: INFRASTRUCTURE_KEY,
      optional: true,
      icon: 'fa fa-xs fa-fw icon-sitemap',
      loader: loadLoadBalancers,
      onLoad: addLoadBalancers,
      afterLoad: addTags,
      providerField: 'cloudProvider',
      credentialsField: 'account',
      regionField: 'region',
      description: 'Traffic distribution management between servers',
      defaultData: [],
    });
  },
]);
