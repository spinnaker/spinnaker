import { UploadArtifactToObjectStorageExecutionDetails } from './UploadArtifactToObjectStorageExecutionDetails';
import { UploadArtifactToObjectStorageStageConfig } from './UploadArtifactToObjectStorageStageConfig';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';
import { validateUploadArtifactToObjectStorageStage } from './uploadArtifactToObjectStorageValidator';

Registry.pipeline.registerStage({
  key: 'uploadArtifactToObjectStorage',
  label: 'Upload Artifact to Object Storage',
  description: 'Upload an artifact to cloud object storage (e.g. S3, GCS)',
  component: UploadArtifactToObjectStorageStageConfig,
  executionDetailsSections: [UploadArtifactToObjectStorageExecutionDetails, ExecutionDetailsTasks],
  validateFn: validateUploadArtifactToObjectStorageStage,
});
