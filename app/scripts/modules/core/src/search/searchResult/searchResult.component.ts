import { module } from 'angular';
import { react2angular } from 'react2angular';

import { SearchResults } from './SearchResults';

export const SEARCH_RESULTS_COMPONENT = 'spinnaker.core.search.result.component';
module(SEARCH_RESULTS_COMPONENT, [])
  .component('searchResults', react2angular(SearchResults, ['searchStatus', 'searchResultCategories', 'searchResultProjects']));
