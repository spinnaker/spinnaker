import { module } from 'angular';

import { ShrinkClusterExecutionDetails } from './ShrinkClusterExecutionDetails';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';

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
