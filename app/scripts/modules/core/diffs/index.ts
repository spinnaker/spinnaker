import {module} from 'angular';

import {VIEW_CHANGES_LINK} from './viewChangesLink.component';

export const CORE_DIFF_MODULE = 'spinnaker.diff.module';
module(CORE_DIFF_MODULE, [VIEW_CHANGES_LINK]);
