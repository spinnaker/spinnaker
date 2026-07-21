import { module } from 'angular';

import { TriggersWrapper } from './TriggersWrapper';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const TRIGGERS = 'spinnaker.core.pipeline.config.trigger.triggersDirective';
module(TRIGGERS, []).component(
  'triggers',
  angularComponentFromReact(TriggersWrapper, 'triggers', [
    'application',
    'pipeline',
    'pipelineConfig',
    'fieldUpdated',
    'updatePipelineConfig',
    'revertCount',
  ]),
);
