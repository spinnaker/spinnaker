import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifactKindConfig } from '../../../../../domain';

import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';

export const OracleMatch: IArtifactKindConfig = {
  label: 'Oracle',
  typePattern: ArtifactTypePatterns.ORACLE_OBJECT,
  type: 'oracle/object',
  description: 'An oracle object.',
  key: 'oracle',
  isDefault: false,
  isMatch: true,
  editCmp: singleFieldArtifactEditor(
    'name',
    'oracle/object',
    'reference',
    'oci://bucket/file-path',
    'pipeline.config.expectedArtifact.oracle.name',
  ),
};

export const OracleDefault: IArtifactKindConfig = {
  label: 'Oracle',
  typePattern: ArtifactTypePatterns.ORACLE_OBJECT,
  type: 'oracle/object',
  description: 'An oracle object.',
  key: 'oracle',
  isDefault: true,
  isMatch: false,
  editCmp: singleFieldArtifactEditor(
    'reference',
    'oracle/object',
    'Reference',
    'oci://bucket/file-path',
    'pipeline.config.expectedArtifact.defaultOracle.reference',
  ),
};
