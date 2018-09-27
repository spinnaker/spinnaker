import { module } from 'angular';

import { Registry } from 'core/registry';
import { ExecutionDetailsTasks } from '../core';
import { DeployServiceExecutionDetails } from './DeployServiceExecutionDetails';

export const DEPLOY_SERVICE_STAGE = 'spinnaker.core.pipeline.stage.deployService';

module(DEPLOY_SERVICE_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionDetailsSections: [DeployServiceExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'deployService',
    label: 'Deploy Service',
    description: 'Deploy a service',
    strategy: true,
  });
});
