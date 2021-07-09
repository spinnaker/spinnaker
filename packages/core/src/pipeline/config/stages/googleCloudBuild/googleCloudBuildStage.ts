import { GoogleCloudBuildExecutionDetails } from './GoogleCloudBuildExecutionDetails';
import { GoogleCloudBuildStageConfig } from './GoogleCloudBuildStageConfig';
import { validate } from './googleCloudBuildValidators';
import {
  ArtifactReferenceService,
  ExecutionArtifactTab,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  Registry,
} from '../../../../index';

Registry.pipeline.registerStage({
  label: 'Google Cloud Build',
  description: 'Trigger a build in GCB (Google Cloud Build)',
  key: 'googleCloudBuild',
  producesArtifacts: true,
  component: GoogleCloudBuildStageConfig,
  executionDetailsSections: [GoogleCloudBuildExecutionDetails, ExecutionDetailsTasks, ExecutionArtifactTab],
  validateFn: validate,
  artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['buildDefinitionArtifact.artifactId']),
  artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['buildDefinitionArtifact.artifactId']),
});
