import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ArtifactList } from './ArtifactList';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const ARTIFACT_LIST = 'spinnaker.core.pipeline.status.artifactList';
module(ARTIFACT_LIST, []).component(
  'artifactList',
  react2angular(withErrorBoundary(ArtifactList, 'artifactList'), ['artifacts']),
);
