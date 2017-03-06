import { module } from 'angular';

import { FEEDBACK_DIRECTIVE } from './feedback.directive';
import { FEEDBACK_MODAL_CONTROLLER } from './feedback.modal.controller';

export const FEEDBACK_MODULE = 'spinnaker.netflix.feedback';

module(FEEDBACK_MODULE, [
  FEEDBACK_MODAL_CONTROLLER,
  FEEDBACK_DIRECTIVE
]);
