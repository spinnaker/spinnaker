import {
  ArtifactReferenceService,
  ExecutionArtifactTab,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  Registry,
} from '@spinnaker/core';

import { PatchManifestStageConfig } from './PatchManifestStageConfig';
import { DeployStatus } from '../deployManifest/manifestStatus/DeployStatus';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';

export class PatchStatus extends DeployStatus {
  public static title = 'PatchStatus';
}

const STAGE_NAME = 'Patch (Manifest)';
Registry.pipeline.registerStage({
  label: STAGE_NAME,
  description: 'Patch a Kubernetes object in place.',
  key: 'patchManifest',
  cloudProvider: 'kubernetes',
  component: PatchManifestStageConfig,
  executionDetailsSections: [PatchStatus, ExecutionDetailsTasks, ExecutionArtifactTab],
  producesArtifacts: true,
  supportsCustomTimeout: true,
  validators: manifestSelectorValidators(STAGE_NAME),
  artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['manifestArtifactId', 'requiredArtifactIds']),
  artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['manifestArtifactId', 'requiredArtifactIds']),
});
