import { module } from 'angular';

import { VIEW_CHANGES_LINK } from './viewChangesLink.component';

export const DIFF_MODULE = 'spinnaker.diff.module';
module(DIFF_MODULE, [VIEW_CHANGES_LINK]);

export * from './viewChangesLink.component';
export * from './ViewChangesLink';
