import { module } from 'angular';

import { SHRINK_CLUSTER_EXECUTION_DETAILS_CTRL } from './templates/shrinkClusterExecutionDetails.controller';
import { SHRINK_CLUSTER_STAGE } from './shrinkClusterStage';

export const SHRINK_CLUSTER_STAGE_MODULE = 'spinnaker.core.pipeline.stage.shrinkCluster';

module(SHRINK_CLUSTER_STAGE_MODULE, [
  SHRINK_CLUSTER_STAGE,
  SHRINK_CLUSTER_EXECUTION_DETAILS_CTRL,
]);
