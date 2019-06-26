import {
  ArtifactReferenceService,
  ExecutionArtifactTab,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  Registry,
  SETTINGS,
} from '@spinnaker/core';

import { DeployStatus } from '../deployManifest/manifestStatus/DeployStatus';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';
import { PatchManifestStageConfig } from './PatchManifestStageConfig';

export class PatchStatus extends DeployStatus {
  public static title = 'PatchStatus';
}

const STAGE_NAME = 'Patch (Manifest)';
if (SETTINGS.feature.versionedProviders) {
  Registry.pipeline.registerStage({
    label: STAGE_NAME,
    description: 'Patch a Kubernetes object in place.',
    key: 'patchManifest',
    cloudProvider: 'kubernetes',
    component: PatchManifestStageConfig,
    executionDetailsSections: [PatchStatus, ExecutionDetailsTasks, ExecutionArtifactTab],
    producesArtifacts: true,
    defaultTimeoutMs: 30 * 60 * 1000, // 30 minutes
    validators: manifestSelectorValidators(STAGE_NAME),
    artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['manifestArtifactId', 'requiredArtifactIds']),
    artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['manifestArtifactId', 'requiredArtifactIds']),
  });
}
