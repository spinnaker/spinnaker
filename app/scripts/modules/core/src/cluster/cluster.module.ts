import { module } from 'angular';

import { CLUSTER_ALLCLUSTERSGROUPINGS } from './allClustersGroupings.component';
import { ON_DEMAND_CLUSTER_PICKER_COMPONENT } from './onDemand/onDemandClusterPicker.component';
import './ClusterSearchResultFormatter';

export const CLUSTER_MODULE = 'spinnaker.core.cluster';

module(CLUSTER_MODULE, [
  require('./allClusters.controller.js'),
  CLUSTER_ALLCLUSTERSGROUPINGS,
  ON_DEMAND_CLUSTER_PICKER_COMPONENT,
]);
