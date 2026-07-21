import { module } from 'angular';

import { ClusterMatches } from './ClusterMatches';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export interface IClusterMatch {
  name: string;
  account: string;
  regions: string[];
}

export const CLUSTER_MATCHES_COMPONENT = 'spinnaker.core.widget.cluster.clusterMatches.component';
module(CLUSTER_MATCHES_COMPONENT, []).component(
  'clusterMatches',
  angularComponentFromReact(ClusterMatches, 'clusterMatches', ['matches']),
);
