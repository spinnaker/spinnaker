import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Triggers } from './Triggers';

export const TRIGGERS = 'spinnaker.core.pipeline.config.trigger.triggersDirective';
module(TRIGGERS, []).component(
  'triggers',
  react2angular(Triggers, ['application', 'pipeline', 'fieldUpdated', 'updatePipelineConfig']),
);
