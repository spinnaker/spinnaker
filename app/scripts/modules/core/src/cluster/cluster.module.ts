import { module } from 'angular';

import './clusterSearchResultType';
import { CLUSTER_ALLCLUSTERSGROUPINGS } from './allClustersGroupings.component';
import { ON_DEMAND_CLUSTER_PICKER_COMPONENT } from './onDemand/onDemandClusterPicker.component';

export const CLUSTER_MODULE = 'spinnaker.core.cluster';

module(CLUSTER_MODULE, [
  require('./allClusters.controller').name,
  CLUSTER_ALLCLUSTERSGROUPINGS,
  ON_DEMAND_CLUSTER_PICKER_COMPONENT,
]);
