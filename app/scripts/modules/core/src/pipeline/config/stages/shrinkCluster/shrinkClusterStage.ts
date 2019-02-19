import { module } from 'angular';

import { Registry } from 'core/registry';

import { ShrinkClusterExecutionDetails } from './ShrinkClusterExecutionDetails';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';

export const SHRINK_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.shrinkClusterStage';

module(SHRINK_CLUSTER_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionDetailsSections: [ShrinkClusterExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'shrinkCluster',
    label: 'Shrink Cluster',
    description: 'Shrinks a cluster',
    strategy: true,
  });
});
