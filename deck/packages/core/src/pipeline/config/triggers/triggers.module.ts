import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TriggersWrapper } from './TriggersWrapper';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';

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
