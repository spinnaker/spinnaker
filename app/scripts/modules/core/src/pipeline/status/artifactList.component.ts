import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ArtifactList } from './ArtifactList';

export const ARTIFACT_LIST = 'spinnaker.core.pipeline.status.artifactList';
module(ARTIFACT_LIST, []).component('artifactList', react2angular(ArtifactList, ['artifacts']));
