import { ExecutionArtifactTab } from 'core/artifact';
import { ExecutionDetailsTasks } from 'core/pipeline';
import { Registry } from 'core/registry';

import { GoogleCloudBuildStageConfig } from './GoogleCloudBuildStageConfig';

Registry.pipeline.registerStage({
  label: 'Google Cloud Build',
  description: 'Trigger a build in GCB (Google Cloud Build)',
  key: 'googleCloudBuild',
  producesArtifacts: true,
  component: GoogleCloudBuildStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks, ExecutionArtifactTab],
  validators: [{ type: 'requiredField', fieldName: 'account' }],
});
