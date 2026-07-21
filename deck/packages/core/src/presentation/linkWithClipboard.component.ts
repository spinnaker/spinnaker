import { module } from 'angular';

import { LinkWithClipboard } from './LinkWithClipboard';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const LINK_WITH_CLIPBOARD = 'spinnaker.core.presentation.linkWithClipboard.component';
module(LINK_WITH_CLIPBOARD, []).component(
  'linkWithClipboard',
  angularComponentFromReact(LinkWithClipboard, 'linkWithClipboard', ['url', 'text']),
);
