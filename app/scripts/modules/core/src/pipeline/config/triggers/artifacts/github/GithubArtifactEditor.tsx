import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const GithubArtifactEditor = singleFieldArtifactEditor(
  'name',
  'File path',
  'manifests/frontend.yaml',
  'pipeline.config.expectedArtifact.git.name',
);
