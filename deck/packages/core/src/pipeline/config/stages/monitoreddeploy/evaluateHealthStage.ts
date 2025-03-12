import { module } from 'angular';

import { DeploymentMonitorExecutionDetails } from './DeploymentMonitorExecutionDetails';
import { Registry } from '../../../../registry';

export const EVALUATE_HEALTH_STAGE = 'spinnaker.core.pipeline.stage.monitored.evaluatehealthstage';

module(EVALUATE_HEALTH_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    synthetic: true,
    key: 'evaluateDeploymentHealth',
    executionDetailsSections: [DeploymentMonitorExecutionDetails],
  });
});
