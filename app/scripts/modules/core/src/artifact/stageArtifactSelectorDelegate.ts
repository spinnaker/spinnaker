import { module } from 'angular';
import { react2angular } from 'react2angular';
import { StageArtifactSelectorDelegate } from './react/StageArtifactSelectorDelegate';

export const STAGE_ARTIFACT_SELECTOR_DELEGATE = 'spinnaker.core.artifact.stageArtifactSelectorDelegate';

module(STAGE_ARTIFACT_SELECTOR_DELEGATE, []).component(
  'stageArtifactSelectorDelegate',
  react2angular(StageArtifactSelectorDelegate, [
    'artifact',
    'excludedArtifactTypePatterns',
    'expectedArtifactId',
    'fieldColumns',
    'helpKey',
    'label',
    'onArtifactEdited',
    'onExpectedArtifactSelected',
    'pipeline',
    'selectedArtifactAccount',
    'selectedArtifactId',
    'setArtifactAccount',
    'setArtifactId',
    'stage',
    'updatePipeline',
  ]),
);
