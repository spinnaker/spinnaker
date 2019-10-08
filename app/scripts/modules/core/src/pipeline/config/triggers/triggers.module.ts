import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Triggers } from './Triggers';
import { ARTIFACT_MODULE } from './artifacts/artifact.module';

export const TRIGGERS = 'spinnaker.core.pipeline.config.trigger.triggersDirective';
module(TRIGGERS, [ARTIFACT_MODULE]).component(
  'triggers',
  react2angular(Triggers, ['application', 'pipeline', 'fieldUpdated', 'updatePipelineConfig', 'viewState']),
);
