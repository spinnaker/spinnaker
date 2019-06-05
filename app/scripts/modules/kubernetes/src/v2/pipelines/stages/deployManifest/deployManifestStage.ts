import {
  ArtifactReferenceService,
  ExecutionArtifactTab,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  IStage,
  Registry,
  SETTINGS,
} from '@spinnaker/core';

import { DeployStatus } from './manifestStatus/DeployStatus';
import { DeployManifestStageConfig } from './DeployManifestStageConfig';
import { deployManifestValidators } from './deployManifest.validator';

// Todo: replace feature flag with proper versioned provider mechanism once available.
if (SETTINGS.feature.versionedProviders) {
  Registry.pipeline.registerStage({
    label: 'Deploy (Manifest)',
    description: 'Deploy a Kubernetes manifest yaml/json file.',
    key: 'deployManifest',
    cloudProvider: 'kubernetes',
    component: DeployManifestStageConfig,
    executionDetailsSections: [DeployStatus, ExecutionDetailsTasks, ExecutionArtifactTab],
    producesArtifacts: true,
    defaultTimeoutMs: 30 * 60 * 1000, // 30 minutes
    validators: deployManifestValidators(),
    accountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
    configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
    artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['manifestArtifactId', 'requiredArtifactIds']),
    artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['manifestArtifactId', 'requiredArtifactIds']),
  });
}
