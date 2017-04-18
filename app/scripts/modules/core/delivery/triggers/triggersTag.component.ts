import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TriggersTag } from './TriggersTag';

export const TRIGGERS_TAG_COMPONENT = 'spinnaker.core.delivery.triggers.triggersTag';
module(TRIGGERS_TAG_COMPONENT, [])
  .component('triggersTag', react2angular(TriggersTag, ['pipeline']));
