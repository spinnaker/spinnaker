import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation';
import { ViewChangesLink } from './ViewChangesLink';

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
