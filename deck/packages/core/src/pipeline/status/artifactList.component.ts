import { module } from 'angular';

import { ArtifactList } from './ArtifactList';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const ARTIFACT_LIST = 'spinnaker.core.pipeline.status.artifactList';
module(ARTIFACT_LIST, []).component(
  'artifactList',
  angularComponentFromReact(ArtifactList, 'artifactList', ['artifacts']),
);
