import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const S3ArtifactEditor = singleFieldArtifactEditor(
  'name',
  'Object path',
  's3://bucket/path/to/file',
  'pipeline.config.expectedArtifact.s3.name',
);
