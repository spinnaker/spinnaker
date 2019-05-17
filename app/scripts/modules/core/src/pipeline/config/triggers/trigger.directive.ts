import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Trigger } from './Trigger';

export const TRIGGER = 'spinnaker.core.pipeline.config.trigger.triggerDirective';
module(TRIGGER, []).component(
  'trigger',
  react2angular(Trigger, [
    'application',
    'index',
    'pipeline',
    'removeTrigger',
    'trigger',
    'updateExpectedArtifacts',
    'updateTrigger',
  ]),
);
