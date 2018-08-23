import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const BitbucketArtifactEditor = singleFieldArtifactEditor(
  'name',
  'File path',
  'manifests/frontend.yaml',
  'pipeline.config.expectedArtifact.git.name',
);
