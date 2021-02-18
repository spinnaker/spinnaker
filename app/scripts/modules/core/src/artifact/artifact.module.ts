import { module } from 'angular';

import { EXECUTION_ARTIFACT_TAB } from './artifactTab';
import { EXPECTED_ARTIFACT_MULTI_SELECTOR_COMPONENT } from './expectedArtifactMultiSelector.component';
import { IMAGE_SOURCE_SELECTOR_COMPONENT } from './imageSourceSelector.component';
import { EXPECTED_ARTIFACT_EDITOR_COMPONENT_REACT } from './react/ExpectedArtifactEditor';
import { EXPECTED_ARTIFACT_SELECTOR_COMPONENT_REACT } from './react/ExpectedArtifactSelector';
import { STAGE_ARTIFACT_SELECTOR_DELEGATE } from './stageArtifactSelectorDelegate';

export const ARTIFACT_MODULE = 'spinnaker.core.artifact';
module(ARTIFACT_MODULE, [
  EXECUTION_ARTIFACT_TAB,
  EXPECTED_ARTIFACT_MULTI_SELECTOR_COMPONENT,
  EXPECTED_ARTIFACT_SELECTOR_COMPONENT_REACT,
  EXPECTED_ARTIFACT_EDITOR_COMPONENT_REACT,
  IMAGE_SOURCE_SELECTOR_COMPONENT,
  STAGE_ARTIFACT_SELECTOR_DELEGATE,
]);
