import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { Spinner } from './Spinner';

export const SPINNER_COMPONENT = 'spinnaker.core.spinner.component';

module(SPINNER_COMPONENT, []).component(
  'loadingSpinner',
  react2angular(withErrorBoundary(Spinner, 'loadingSpinner'), ['size', 'message']),
);
