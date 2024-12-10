import { ExecutionArtifactTab, ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { FindArtifactsFromResourceConfig } from './FindArtifactsFromResourceConfig';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';

const STAGE_NAME = 'Find Artifacts From Resource (Manifest)';
const STAGE_KEY = 'findArtifactsFromResource';

Registry.pipeline.registerStage({
  label: STAGE_NAME,
  description: 'Finds artifacts from a Kubernetes resource.',
  key: STAGE_KEY,
  cloudProvider: 'kubernetes',
  component: FindArtifactsFromResourceConfig,
  executionDetailsSections: [ExecutionDetailsTasks, ExecutionArtifactTab],
  producesArtifacts: true,
  validators: manifestSelectorValidators(STAGE_NAME),
});
