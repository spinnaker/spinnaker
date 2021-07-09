import { BakeManifestConfig, validateBakeManifestStage } from './BakeManifestConfig';
import { BakeManifestDetailsTab } from './BakeManifestDetailsTab';
import { ManualExecutionBakeManifest } from './ManualExecutionBakeManifest';
import { ArtifactReferenceService, ExecutionArtifactTab, ExpectedArtifactService } from '../../../../artifact';
import { ExecutionDetailsTasks } from '../common';
import { IArtifact, IStage } from '../../../../domain';
import { Registry } from '../../../../registry';

export const BAKE_MANIFEST_STAGE_KEY = 'bakeManifest';

Registry.pipeline.registerStage({
  label: 'Bake (Manifest)',
  description: 'Bake a manifest (or multi-doc manifest set) using a template renderer such as Helm.',
  key: BAKE_MANIFEST_STAGE_KEY,
  component: BakeManifestConfig,
  producesArtifacts: true,
  cloudProvider: 'kubernetes',
  executionDetailsSections: [BakeManifestDetailsTab, ExecutionDetailsTasks, ExecutionArtifactTab],
  artifactExtractor: (fields: string[]) =>
    ExpectedArtifactService.accumulateArtifacts<IArtifact>(['inputArtifacts'])(fields).map((a: IArtifact) => a.id),
  artifactRemover: (stage: IStage, artifactId: string) => {
    ArtifactReferenceService.removeArtifactFromFields(['expectedArtifactId'])(stage, artifactId);

    const artifactDoesNotMatch = (artifact: IArtifact) => artifact.id !== artifactId;
    stage.expectedArtifacts = (stage.expectedArtifacts ?? []).filter(artifactDoesNotMatch);
    stage.inputArtifacts = (stage.inputArtifacts ?? []).filter(artifactDoesNotMatch);
  },
  validateFn: validateBakeManifestStage,
  manualExecutionComponent: ManualExecutionBakeManifest,
});
