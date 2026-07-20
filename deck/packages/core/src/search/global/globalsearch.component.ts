import { module } from 'angular';

import { GlobalSearch } from './GlobalSearch';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const GLOBAL_SEARCH_COMPONENT = 'spinnaker.core.search.global.component';
module(GLOBAL_SEARCH_COMPONENT, []).component('globalSearch', angularComponentFromReact(GlobalSearch, 'globalSearch'));
