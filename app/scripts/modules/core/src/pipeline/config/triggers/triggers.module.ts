import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TriggersWrapper } from './TriggersWrapper';

export const TRIGGERS = 'spinnaker.core.pipeline.config.trigger.triggersDirective';
module(TRIGGERS, []).component(
  'triggers',
  react2angular(TriggersWrapper, ['application', 'pipeline', 'fieldUpdated', 'updatePipelineConfig', 'viewState']),
);
