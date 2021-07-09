import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ClusterFilters } from './ClusterFilters';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';
export const CLUSTER_FILTER = 'spinnaker.core.cluster.filter.component';

module(CLUSTER_FILTER, []).component(
  'clusterFilter',
  react2angular(withErrorBoundary(ClusterFilters, 'clusterFilter'), ['app']),
);
