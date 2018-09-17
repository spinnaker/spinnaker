import { module } from 'angular';

import { EXPECTED_ARTIFACT_SELECTOR_COMPONENT_REACT } from './react/ExpectedArtifactSelector';
import { EXPECTED_ARTIFACT_EDITOR_COMPONENT_REACT } from './react/ExpectedArtifactEditor';
import { SUMMARIZE_EXPECTED_ARTIFACT_FILTER } from './summarizeExpectedArtifact';
import { EXPECTED_ARTIFACT_MULTI_SELECTOR_COMPONENT } from './expectedArtifactMultiSelector.component';
import { IMAGE_SOURCE_SELECTOR_COMPONENT } from './imageSourceSelector.component';
import { EXECUTION_ARTIFACT_TAB } from './artifactTab';

export const ARTIFACT_MODULE = 'spinnaker.core.artifact';
module(ARTIFACT_MODULE, [
  EXECUTION_ARTIFACT_TAB,
  EXPECTED_ARTIFACT_MULTI_SELECTOR_COMPONENT,
  EXPECTED_ARTIFACT_SELECTOR_COMPONENT_REACT,
  EXPECTED_ARTIFACT_EDITOR_COMPONENT_REACT,
  IMAGE_SOURCE_SELECTOR_COMPONENT,
  SUMMARIZE_EXPECTED_ARTIFACT_FILTER,
]);
