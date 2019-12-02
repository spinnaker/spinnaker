import { module } from 'angular';

import './clusterSearchResultType';
import { CLUSTER_ALLCLUSTERSGROUPINGS } from './allClustersGroupings.component';
import { ON_DEMAND_CLUSTER_PICKER_COMPONENT } from './onDemand/onDemandClusterPicker.component';
import { CORE_CLUSTER_ALLCLUSTERS_CONTROLLER } from './allClusters.controller';

export const CLUSTER_MODULE = 'spinnaker.core.cluster';

module(CLUSTER_MODULE, [
  CORE_CLUSTER_ALLCLUSTERS_CONTROLLER,
  CLUSTER_ALLCLUSTERSGROUPINGS,
  ON_DEMAND_CLUSTER_PICKER_COMPONENT,
]);
