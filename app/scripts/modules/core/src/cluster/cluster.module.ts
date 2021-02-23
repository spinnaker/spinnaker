import { module } from 'angular';

import { CORE_CLUSTER_ALLCLUSTERS_CONTROLLER } from './allClusters.controller';
import { CLUSTER_ALLCLUSTERSGROUPINGS } from './allClustersGroupings.component';
import './clusterSearchResultType';
import { ON_DEMAND_CLUSTER_PICKER_COMPONENT } from './onDemand/onDemandClusterPicker.component';

export const CLUSTER_MODULE = 'spinnaker.core.cluster';

module(CLUSTER_MODULE, [
  CORE_CLUSTER_ALLCLUSTERS_CONTROLLER,
  CLUSTER_ALLCLUSTERSGROUPINGS,
  ON_DEMAND_CLUSTER_PICKER_COMPONENT,
]);
