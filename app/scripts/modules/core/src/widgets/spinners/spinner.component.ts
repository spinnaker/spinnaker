import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Spinner } from './Spinner';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const SPINNER_COMPONENT = 'spinnaker.core.spinner.component';

module(SPINNER_COMPONENT, []).component(
  'loadingSpinner',
  react2angular(withErrorBoundary(Spinner, 'loadingSpinner'), ['size', 'message', 'mode']),
);
