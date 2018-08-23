import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const DockerArtifactEditor = singleFieldArtifactEditor(
  'name',
  'Docker image',
  'gcr.io/project/image',
  'pipeline.config.expectedArtifact.docker.name',
);
