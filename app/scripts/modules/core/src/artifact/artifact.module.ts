import { module } from 'angular';

import { EXPECTED_ARTIFACT_SELECTOR_COMPONENT } from './expectedArtifactSelector.component';

export const ARTIFACT_MODULE = 'spinnaker.core.artifact';
module(ARTIFACT_MODULE, [
  EXPECTED_ARTIFACT_SELECTOR_COMPONENT,
]);
