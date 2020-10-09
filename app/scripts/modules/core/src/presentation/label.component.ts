import { withErrorBoundary } from './SpinErrorBoundary';
import { module } from 'angular';
import { react2angular } from 'react2angular';

import { LabelComponent } from './LabelComponent';

export const LABEL_COMPONENT = 'spinnaker.core.presentation.label.component';
module(LABEL_COMPONENT, []).component(
  'labelComponent',
  react2angular(withErrorBoundary(LabelComponent, 'labelComponent'), ['stage']),
);
