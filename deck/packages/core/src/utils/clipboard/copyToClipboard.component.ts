import { module } from 'angular';

import { CopyToClipboard } from './CopyToClipboard';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const COPY_TO_CLIPBOARD_COMPONENT = 'spinnaker.core.utils.copyToClipboard.directive';
module(COPY_TO_CLIPBOARD_COMPONENT, []).component(
  'copyToClipboard',
  angularComponentFromReact(CopyToClipboard, 'copyToClipboard', [
    'analyticsLabel',
    'buttonInnerNode',
    'displayText',
    'text',
    'toolTip',
    'className',
    'stopPropagation',
  ]),
);
