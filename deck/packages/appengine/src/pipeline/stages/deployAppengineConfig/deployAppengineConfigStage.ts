import { ExecutionArtifactTab, ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import {
  DeployAppengineConfigurationConfig,
  validateDeployAppengineConfigurationStage,
} from './DeployAppengineConfigurationConfig';

export const DEPLOY_APPENGINE_CONFIG_STAGE_KEY = 'deployAppEngineConfiguration';

Registry.pipeline.registerStage({
  label: 'Deploy App Engine Configuration',
  description: 'Deploy index, dispatch, cron, and queue configuration to App Engine.',
  key: DEPLOY_APPENGINE_CONFIG_STAGE_KEY,
  component: DeployAppengineConfigurationConfig,
  producesArtifacts: false,
  cloudProvider: 'appengine',
  executionDetailsSections: [ExecutionDetailsTasks, ExecutionArtifactTab],
  validateFn: validateDeployAppengineConfigurationStage,
});
