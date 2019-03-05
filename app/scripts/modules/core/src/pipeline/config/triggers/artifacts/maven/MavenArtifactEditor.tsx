import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifactKindConfig } from 'core/domain';

import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const MavenMatch: IArtifactKindConfig = {
  label: 'Maven',
  typePattern: ArtifactTypePatterns.MAVEN_FILE,
  type: 'maven/file',
  description: 'A Maven repository artifact.',
  key: 'maven',
  isDefault: false,
  isMatch: true,
  editCmp: singleFieldArtifactEditor('name', 'maven/file', 'Maven Coordinate', 'group:artifact:version', ''),
};

export const MavenDefault: IArtifactKindConfig = {
  label: 'Maven',
  typePattern: ArtifactTypePatterns.MAVEN_FILE,
  type: 'maven/file',
  description: 'A Maven repository artifact.',
  key: 'maven',
  isDefault: true,
  isMatch: false,
  editCmp: singleFieldArtifactEditor('name', 'maven/file', 'Maven Coordinate', 'group:artifact:version', ''),
};
