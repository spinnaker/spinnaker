import { module } from 'angular';

import { EXPECTED_ARTIFACT_SELECTOR_COMPONENT } from './expectedArtifactSelector.component';
import { EXPECTED_ARTIFACT_SERVICE } from './expectedArtifact.service';
import { EXPECTED_ARTIFACT_MULTI_SELECTOR_COMPONENT } from './expectedArtifactMultiSelector.component';

export const ARTIFACT_MODULE = 'spinnaker.core.artifact';
module(ARTIFACT_MODULE, [
  EXPECTED_ARTIFACT_MULTI_SELECTOR_COMPONENT,
  EXPECTED_ARTIFACT_SELECTOR_COMPONENT,
  EXPECTED_ARTIFACT_SERVICE,
]);
