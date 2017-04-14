import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Feedback } from './Feedback';

export const FEEDBACK_COMPONENT = 'spinnaker.netflix.feedback';
module(FEEDBACK_COMPONENT, [])
  .component('feedback', react2angular(Feedback));
