import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { manifestExecutionDetails } from '../ManifestExecutionDetails';
import { ScaleManifestStageConfig } from './ScaleManifestConfig';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';

const STAGE_NAME = 'Scale (Manifest)';
const STAGE_KEY = 'scaleManifest';

Registry.pipeline.registerStage({
  label: STAGE_NAME,
  description: 'Scale a Kubernetes object created from a manifest.',
  key: STAGE_KEY,
  cloudProvider: 'kubernetes',
  component: ScaleManifestStageConfig,
  executionDetailsSections: [manifestExecutionDetails(STAGE_KEY), ExecutionDetailsTasks],
  accountExtractor: (stage: IStage): string[] => (stage.context.account ? [stage.context.account] : []),
  configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
  validators: [
    ...manifestSelectorValidators(STAGE_NAME),
    { type: 'requiredField', fieldName: 'replicas', fieldLabel: 'Replicas' },
  ],
});
