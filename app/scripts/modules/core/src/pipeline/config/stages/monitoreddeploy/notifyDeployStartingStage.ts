import { module } from 'angular';

import { Registry } from 'core/registry';

import { DeploymentMonitorExecutionDetails } from './DeploymentMonitorExecutionDetails';

export const NOTIFY_DEPLOY_STARTING_STAGE = 'spinnaker.core.pipeline.stage.monitored.notifydeploystartingstage';

module(NOTIFY_DEPLOY_STARTING_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    synthetic: true,
    key: 'notifyDeployStarting',
    executionDetailsSections: [DeploymentMonitorExecutionDetails],
  });
});
