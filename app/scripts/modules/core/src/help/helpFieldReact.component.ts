import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { HelpField } from './HelpField';

export const HELP_FIELD_REACT_COMPONENT = 'spinnaker.core.help.helpFieldReact.component';

module(HELP_FIELD_REACT_COMPONENT, []).component(
  'helpFieldReact',
  react2angular(withErrorBoundary(HelpField, 'helpFieldReact'), [
    'content',
    'expand',
    'fallback',
    'id',
    'label',
    'placement',
  ]),
);
