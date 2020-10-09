import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Spinner } from './Spinner';

export const SPINNER_COMPONENT = 'spinnaker.core.spinner.component';

module(SPINNER_COMPONENT, []).component(
  'loadingSpinner',
  react2angular(withErrorBoundary(Spinner, 'loadingSpinner'), ['size', 'message']),
);
