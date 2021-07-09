import { module } from 'angular';

import { RollbackClusterExecutionDetails } from './RollbackClusterExecutionDetails';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';

export const ROLLBACK_CLUSTER_STAGE = 'spinnaker.core.pipeline.stage.rollbackClusterStage';
module(ROLLBACK_CLUSTER_STAGE, []).config(function () {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'rollbackCluster',
    label: 'Rollback Cluster',
    description: 'Rollback one or more regions in a cluster',
    executionDetailsSections: [RollbackClusterExecutionDetails, ExecutionDetailsTasks],
  });
});
