import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { ClusterMatches } from './ClusterMatches';

export interface IClusterMatch {
  name: string;
  account: string;
  regions: string[];
}

export const CLUSTER_MATCHES_COMPONENT = 'spinnaker.core.widget.cluster.clusterMatches.component';
module(CLUSTER_MATCHES_COMPONENT, []).component(
  'clusterMatches',
  react2angular(withErrorBoundary(ClusterMatches, 'clusterMatches'), ['matches']),
);
