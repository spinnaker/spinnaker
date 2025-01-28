import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { manifestExecutionDetails } from '../ManifestExecutionDetails';
import { UndoRolloutManifestConfig } from './UndoRolloutManifestConfig';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';

const STAGE_NAME = 'Undo Rollout (Manifest)';
const STAGE_KEY = 'undoRolloutManifest';

Registry.pipeline.registerStage({
  label: STAGE_NAME,
  description: 'Rollback a manifest a target number of revisions.',
  key: STAGE_KEY,
  cloudProvider: 'kubernetes',
  component: UndoRolloutManifestConfig,
  executionDetailsSections: [manifestExecutionDetails(STAGE_KEY), ExecutionDetailsTasks],
  validators: [
    ...manifestSelectorValidators(STAGE_NAME),
    { type: 'requiredField', fieldName: 'numRevisionsBack', fieldLabel: 'Number of Revisions' },
  ],
});
