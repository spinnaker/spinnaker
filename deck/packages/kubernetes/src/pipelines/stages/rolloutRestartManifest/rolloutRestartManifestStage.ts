import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { RolloutRestartManifestStageConfig } from './RolloutRestartManifestStageConfig';
import { DeployStatus } from '../deployManifest/manifestStatus/DeployStatus';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';

export class RolloutRestartStatus extends DeployStatus {
  public static title = 'RolloutRestartStatus';
}

const STAGE_NAME = 'Rollout Restart (Manifest)';
Registry.pipeline.registerStage({
  label: STAGE_NAME,
  description: 'Perform a rolling restart of a manifest.',
  key: 'rollingRestartManifest',
  cloudProvider: 'kubernetes',
  component: RolloutRestartManifestStageConfig,
  executionDetailsSections: [RolloutRestartStatus, ExecutionDetailsTasks],
  validators: manifestSelectorValidators(STAGE_NAME),
});
