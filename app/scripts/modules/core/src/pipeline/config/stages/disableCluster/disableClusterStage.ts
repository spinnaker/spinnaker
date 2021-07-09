import { module } from 'angular';

import { DisableClusterExecutionDetails } from './DisableClusterExecutionDetails';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';

export const DISABLE_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.disableClusterStage';
module(DISABLE_CLUSTER_STAGE, []).config(function () {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'disableCluster',
    label: 'Disable Cluster',
    description: 'Disables a cluster',
    executionDetailsSections: [DisableClusterExecutionDetails, ExecutionDetailsTasks],
    strategy: true,
  });
});
