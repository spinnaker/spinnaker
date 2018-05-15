import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TriggerTemplate } from './TriggerTemplate';

export const TRIGGER_TEMPLATE = 'spinnaker.core.pipeline.manualExecution.triggerTemplate.component';
module(TRIGGER_TEMPLATE, []).component('triggerTemplate', react2angular(TriggerTemplate, ['component', 'command']));
