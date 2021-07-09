import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ViewChangesLink } from './ViewChangesLink';
import { withErrorBoundary } from '../presentation';

export const VIEW_CHANGES_LINK = 'spinnaker.diffs.view.changes.link';
module(VIEW_CHANGES_LINK, []).component(
  'viewChangesLink',
  react2angular(withErrorBoundary(ViewChangesLink, 'viewChangesLink'), [
    'changeConfig',
    'linkText',
    'nameItem',
    'viewType',
  ]),
);
