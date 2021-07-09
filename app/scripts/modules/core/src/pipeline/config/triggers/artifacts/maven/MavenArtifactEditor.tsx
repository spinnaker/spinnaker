import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifactKindConfig } from '../../../../../domain';

import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const MavenMatch: IArtifactKindConfig = {
  label: 'Maven',
  typePattern: ArtifactTypePatterns.MAVEN_FILE,
  type: 'maven/file',
  description: 'A Maven repository artifact.',
  key: 'maven',
  isDefault: false,
  isMatch: true,
  editCmp: singleFieldArtifactEditor('reference', 'maven/file', 'Maven Coordinate', 'group:artifact:version', ''),
};

export const MavenDefault: IArtifactKindConfig = {
  label: 'Maven',
  typePattern: ArtifactTypePatterns.MAVEN_FILE,
  type: 'maven/file',
  description: 'A Maven repository artifact.',
  key: 'maven',
  isDefault: true,
  isMatch: false,
  editCmp: singleFieldArtifactEditor('reference', 'maven/file', 'Maven Coordinate', 'group:artifact:version', ''),
};
