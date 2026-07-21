import { module } from 'angular';

import { ViewChangesLink } from './ViewChangesLink';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const VIEW_CHANGES_LINK = 'spinnaker.diffs.view.changes.link';
module(VIEW_CHANGES_LINK, []).component(
  'viewChangesLink',
  angularComponentFromReact(ViewChangesLink, 'viewChangesLink', ['changeConfig', 'linkText', 'nameItem', 'viewType']),
);
