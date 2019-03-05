import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifactKindConfig } from 'core/domain';

import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const HttpMatch: IArtifactKindConfig = {
  label: 'HTTP',
  typePattern: ArtifactTypePatterns.HTTP_FILE,
  type: 'http/file',
  description: 'An HTTP artifact.',
  key: 'http',
  isDefault: false,
  isMatch: true,
  editCmp: singleFieldArtifactEditor('name', 'http/file', 'URL', 'path/file.ext', ''),
};

export const HttpDefault: IArtifactKindConfig = {
  label: 'HTTP',
  typePattern: ArtifactTypePatterns.HTTP_FILE,
  type: 'http/file',
  description: 'An HTTP artifact.',
  key: 'default.http',
  isDefault: true,
  isMatch: false,
  editCmp: singleFieldArtifactEditor('name', 'http/file', 'URL', 'http://host/path/file.ext', ''),
};
