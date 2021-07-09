import { module } from 'angular';

import { DeploymentMonitorExecutionDetails } from './DeploymentMonitorExecutionDetails';
import { Registry } from '../../../../registry';

export const NOTIFY_DEPLOY_STARTING_STAGE = 'spinnaker.core.pipeline.stage.monitored.notifydeploystartingstage';

module(NOTIFY_DEPLOY_STARTING_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    synthetic: true,
    key: 'notifyDeployStarting',
    executionDetailsSections: [DeploymentMonitorExecutionDetails],
  });
});
