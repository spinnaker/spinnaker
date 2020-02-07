import { module } from 'angular';
import { react2angular } from 'react2angular';
import { LinkWithClipboard } from './LinkWithClipboard';

export const LINK_WITH_CLIPBOARD = 'spinnaker.core.presentation.linkWithClipboard.component';
module(LINK_WITH_CLIPBOARD, []).component('linkWithClipboard', react2angular(LinkWithClipboard, ['url', 'text']));
