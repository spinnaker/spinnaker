import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { TriggersWrapper } from './TriggersWrapper';

export const TRIGGERS = 'spinnaker.core.pipeline.config.trigger.triggersDirective';
module(TRIGGERS, []).component(
  'triggers',
  react2angular(withErrorBoundary(TriggersWrapper, 'triggers'), [
    'application',
    'pipeline',
    'fieldUpdated',
    'updatePipelineConfig',
    'revertCount',
  ]),
);
