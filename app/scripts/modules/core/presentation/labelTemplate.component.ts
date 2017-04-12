import { module } from 'angular';
import { react2angular } from 'react2angular';

import { LabelTemplate } from './LabelTemplate';

export const LABEL_TEMPLATE_COMPONENT = 'spinnaker.core.presentation.labelTemplate';
module(LABEL_TEMPLATE_COMPONENT, [])
  .component('labelTemplate', react2angular(LabelTemplate, ['stage']));
