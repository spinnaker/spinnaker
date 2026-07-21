import { module } from 'angular';

import { SearchResult } from './SearchResult';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const SEARCH_RESULT_COMPONENT = 'spinnaker.core.search.infrastructure.searchResult.component';
module(SEARCH_RESULT_COMPONENT, []).component(
  'searchResult',
  angularComponentFromReact(SearchResult, 'searchResult', ['displayName', 'account']),
);
