import { module } from 'angular';
import { react2angular } from 'react2angular';

import { SearchResult } from './SearchResult';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const SEARCH_RESULT_COMPONENT = 'spinnaker.core.search.infrastructure.searchResult.component';
module(SEARCH_RESULT_COMPONENT, []).component(
  'searchResult',
  react2angular(withErrorBoundary(SearchResult, 'searchResult'), ['displayName', 'account']),
);
