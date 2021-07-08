import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation';
import { CopyToClipboard } from './CopyToClipboard';

export const COPY_TO_CLIPBOARD_COMPONENT = 'spinnaker.core.utils.copyToClipboard.directive';
module(COPY_TO_CLIPBOARD_COMPONENT, []).component(
  'copyToClipboard',
  react2angular(withErrorBoundary(CopyToClipboard, 'copyToClipboard'), [
    'analyticsLabel',
    'buttonInnerNode',
    'displayText',
    'text',
    'toolTip',
    'className',
    'stopPropagation',
  ]),
);
