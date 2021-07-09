import { module } from 'angular';

import { ScaleDownClusterExecutionDetails } from './ScaleDownClusterExecutionDetails';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';

export const SCALE_DOWN_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.scaleDownClusterStage';
module(SCALE_DOWN_CLUSTER_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionDetailsSections: [ScaleDownClusterExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'scaleDownCluster',
    label: 'Scale Down Cluster',
    description: 'Scales down a cluster',
    strategy: true,
  });
});
