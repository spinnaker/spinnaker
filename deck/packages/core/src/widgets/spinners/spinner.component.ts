import { module } from 'angular';

import { Spinner } from './Spinner';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const SPINNER_COMPONENT = 'spinnaker.core.spinner.component';

module(SPINNER_COMPONENT, []).component(
  'loadingSpinner',
  angularComponentFromReact(Spinner, 'loadingSpinner', ['size', 'message', 'mode']),
);
