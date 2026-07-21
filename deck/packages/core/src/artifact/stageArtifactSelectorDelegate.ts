import { module } from 'angular';

import { angularComponentFromReact } from '../angular/angularComponentFromReact';

import { StageArtifactSelectorDelegate } from './react/StageArtifactSelectorDelegate';

export const STAGE_ARTIFACT_SELECTOR_DELEGATE = 'spinnaker.core.artifact.stageArtifactSelectorDelegate';

module(STAGE_ARTIFACT_SELECTOR_DELEGATE, []).component(
  'stageArtifactSelectorDelegate',
  angularComponentFromReact(StageArtifactSelectorDelegate, 'stageArtifactSelectorDelegate', [
    'artifact',
    'excludedArtifactTypePatterns',
    'expectedArtifactId',
    'fieldColumns',
    'helpKey',
    'label',
    'onArtifactEdited',
    'onExpectedArtifactSelected',
    'pipeline',
    'stage',
  ]),
);
