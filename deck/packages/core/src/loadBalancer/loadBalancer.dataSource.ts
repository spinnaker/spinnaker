import type { Application } from '../application/application.model';
import { INFRASTRUCTURE_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { ILoadBalancer } from '../domain';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import type { LoadBalancerReader } from './loadBalancer.read.service';
import { addManagedResourceMetadataToLoadBalancers } from '../managed';
import type { PromiseService } from '../utils/nativePromiseService';

function createDataSourceConfig(
  resolve: <T>(value: T | PromiseLike<T>) => Promise<Awaited<T>>,
  loadBalancerReader: LoadBalancerReader,
) {
  const loadLoadBalancers = (application: Application) => {
    return loadBalancerReader.loadLoadBalancers(application.name);
  };

  const addLoadBalancers = (_application: Application, loadBalancers: ILoadBalancer[]) => {
    return resolve(loadBalancers);
  };

  const addTags = (application: Application) => {
    EntityTagsReader.addTagsToLoadBalancers(application);
    addManagedResourceMetadataToLoadBalancers(application);
  };

  return {
    key: 'loadBalancers',
    sref: '.insight.loadBalancers',
    category: INFRASTRUCTURE_KEY,
    optional: true,
    icon: 'fa fa-xs fa-fw icon-sitemap',
    iconName: 'spMenuLoadBalancers' as const,
    loader: loadLoadBalancers,
    onLoad: addLoadBalancers,
    afterLoad: addTags,
    providerField: 'cloudProvider',
    credentialsField: 'account',
    regionField: 'region',
    description: 'Traffic distribution management between servers',
    defaultData: [] as ILoadBalancer[],
  };
}

export function registerLoadBalancerDataSource(
  promiseService: PromiseService,
  loadBalancerReader: LoadBalancerReader,
): void {
  if (ApplicationDataSourceRegistry.getDataSources().some((source) => source.key === 'loadBalancers')) {
    return;
  }

  ApplicationDataSourceRegistry.registerDataSource(
    createDataSourceConfig(<T>(value: T | PromiseLike<T>) => promiseService.resolve(value), loadBalancerReader),
  );
}
