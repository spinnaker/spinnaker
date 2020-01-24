import { Registry, IStage, ExecutionDetailsTasks } from '@spinnaker/core';

import { manifestExecutionDetails } from '../ManifestExecutionDetails';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';
import { DeleteManifestStageConfig } from './DeleteManifestStageConfig';

const STAGE_NAME = 'Delete (Manifest)';
const STAGE_KEY = 'deleteManifest';

Registry.pipeline.registerStage({
  label: STAGE_NAME,
  description: 'Destroy a Kubernetes object created from a manifest.',
  key: STAGE_KEY,
  cloudProvider: 'kubernetes',
  component: DeleteManifestStageConfig,
  executionDetailsSections: [manifestExecutionDetails(STAGE_KEY), ExecutionDetailsTasks],
  accountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
  configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
  validators: manifestSelectorValidators(STAGE_NAME),
});
