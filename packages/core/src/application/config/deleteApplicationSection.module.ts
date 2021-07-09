import { module } from 'angular';
import { react2angular } from 'react2angular';

import { DeleteApplicationSection } from './DeleteApplicationSection';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';
export const DELETE_APPLICATION_SECTION = 'spinnaker.core.application.config.delete.directive';
module(DELETE_APPLICATION_SECTION, []).component(
  'deleteApplicationSection',
  react2angular(withErrorBoundary(DeleteApplicationSection, 'deleteApplicationSection'), ['application']),
);
