import type { IStage, IStageTypeConfig } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { manifestExecutionDetails } from '../ManifestExecutionDetails';
import { ManifestTrafficStageConfig } from './ManifestTrafficStageConfig';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';

const STAGE_NAME = 'Enable (Manifest)';
const STAGE_KEY = 'enableManifest';

export const ENABLE_MANIFEST_STAGE_CONFIG: IStageTypeConfig = {
  label: STAGE_NAME,
  description: 'Enable a Kubernetes manifest.',
  key: STAGE_KEY,
  cloudProvider: 'kubernetes',
  component: ManifestTrafficStageConfig,
  executionDetailsSections: [manifestExecutionDetails(STAGE_KEY), ExecutionDetailsTasks],
  supportsCustomTimeout: true,
  accountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
  configAccountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
  validators: manifestSelectorValidators(STAGE_NAME),
};

Registry.pipeline.registerStage(ENABLE_MANIFEST_STAGE_CONFIG);
