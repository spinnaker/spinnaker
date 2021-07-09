import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifactKindConfig } from '../../../../../domain';

import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const IvyMatch: IArtifactKindConfig = {
  label: 'Ivy',
  typePattern: ArtifactTypePatterns.IVY_FILE,
  type: 'ivy/file',
  description: 'An Ivy repository artifact.',
  key: 'ivy',
  isDefault: false,
  isMatch: true,
  editCmp: singleFieldArtifactEditor('reference', 'ivy/file', 'Ivy Coordinate', 'group:artifact:version', ''),
};

export const IvyDefault: IArtifactKindConfig = {
  label: 'Ivy',
  typePattern: ArtifactTypePatterns.IVY_FILE,
  type: 'ivy/file',
  description: 'An Ivy repository artifact.',
  key: 'ivy',
  isDefault: true,
  isMatch: false,
  editCmp: singleFieldArtifactEditor('reference', 'ivy/file', 'Ivy Coordinate', 'group:artifact:version', ''),
};
