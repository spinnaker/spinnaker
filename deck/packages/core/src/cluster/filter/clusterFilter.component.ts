import { module } from 'angular';

import { ClusterFilters } from './ClusterFilters';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';
export const CLUSTER_FILTER = 'spinnaker.core.cluster.filter.component';

module(CLUSTER_FILTER, []).component(
  'clusterFilter',
  angularComponentFromReact(ClusterFilters, 'clusterFilter', ['app']),
);
