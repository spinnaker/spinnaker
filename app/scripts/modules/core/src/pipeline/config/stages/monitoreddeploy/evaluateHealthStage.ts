import { module } from 'angular';

import { Registry } from 'core/registry';

import { DeploymentMonitorExecutionDetails } from './DeploymentMonitorExecutionDetails';

export const EVALUATE_HEALTH_STAGE = 'spinnaker.core.pipeline.stage.monitored.evaluatehealthstage';

module(EVALUATE_HEALTH_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    synthetic: true,
    key: 'evaluateDeploymentHealth',
    executionDetailsSections: [DeploymentMonitorExecutionDetails],
  });
});
