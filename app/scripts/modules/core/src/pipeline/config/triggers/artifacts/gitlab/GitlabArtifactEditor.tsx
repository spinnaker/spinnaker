import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const GitlabArtifactEditor = singleFieldArtifactEditor(
  'name',
  'File path',
  'manifests/frontend.yaml',
  'pipeline.config.expectedArtifact.git.name',
);
