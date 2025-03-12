import { module } from 'angular';

import { PAGE_TITLE_SERVICE } from './pageTitle.service';

export const PAGE_TITLE_MODULE = 'spinnaker.core.pageTitle';
module(PAGE_TITLE_MODULE, [PAGE_TITLE_SERVICE]);
