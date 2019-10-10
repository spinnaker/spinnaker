import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TriggersWrapper } from './TriggersWrapper';
import { ARTIFACT_MODULE } from './artifacts/artifact.module';

export const TRIGGERS = 'spinnaker.core.pipeline.config.trigger.triggersDirective';
module(TRIGGERS, [ARTIFACT_MODULE]).component(
  'triggers',
  react2angular(TriggersWrapper, ['application', 'pipeline', 'fieldUpdated', 'updatePipelineConfig', 'viewState']),
);
