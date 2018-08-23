import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const GcsArtifactEditor = singleFieldArtifactEditor(
  'name',
  'Object path',
  'gs://bucket/path/to/file',
  'pipeline.config.expectedArtifact.gcs.name',
);
