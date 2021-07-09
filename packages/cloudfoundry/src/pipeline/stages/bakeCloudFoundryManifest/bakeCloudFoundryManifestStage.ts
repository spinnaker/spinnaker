import {
  ArtifactReferenceService,
  ExecutionArtifactTab,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  IArtifact,
  IStage,
  Registry,
} from '@spinnaker/core';

import {
  BakeCloudFoundryManifestConfig,
  validateBakeCloudFoundryManifestStage,
} from './BakeCloudFoundryManifestConfig';
import { BakeCloudFoundryManifestDetailsTab } from './BakeCloudFoundryManifestDetailsTab';

export const BAKE_CF_MANIFEST_STAGE_KEY = 'bakeCloudFoundryManifest';

Registry.pipeline.registerStage({
  label: 'Bake CloudFoundry Manifest',
  description: 'Bake a CF manifest using external variables files.',
  key: BAKE_CF_MANIFEST_STAGE_KEY,
  component: BakeCloudFoundryManifestConfig,
  producesArtifacts: true,
  cloudProvider: 'cloudfoundry',
  executionDetailsSections: [BakeCloudFoundryManifestDetailsTab, ExecutionDetailsTasks, ExecutionArtifactTab],
  artifactExtractor: (fields: string[]) =>
    ExpectedArtifactService.accumulateArtifacts<IArtifact>(['inputArtifacts'])(fields).map((a: IArtifact) => a.id),
  artifactRemover: (stage: IStage, artifactId: string) => {
    ArtifactReferenceService.removeArtifactFromFields(['expectedArtifactId'])(stage, artifactId);
    const artifactDoesNotMatch = (artifact: IArtifact) => artifact.id !== artifactId;
    stage.expectedArtifacts = (stage.expectedArtifacts ?? []).filter(artifactDoesNotMatch);
    stage.inputArtifacts = (stage.inputArtifacts ?? []).filter(artifactDoesNotMatch);
  },
  validateFn: validateBakeCloudFoundryManifestStage,
});
