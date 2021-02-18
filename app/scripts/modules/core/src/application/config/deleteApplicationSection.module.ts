import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { DeleteApplicationSection } from './DeleteApplicationSection';
export const DELETE_APPLICATION_SECTION = 'spinnaker.core.application.config.delete.directive';
module(DELETE_APPLICATION_SECTION, []).component(
  'deleteApplicationSection',
  react2angular(withErrorBoundary(DeleteApplicationSection, 'deleteApplicationSection'), ['application']),
);
