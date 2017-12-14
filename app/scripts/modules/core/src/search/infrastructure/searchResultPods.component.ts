import { module } from 'angular';
import { react2angular } from 'react2angular';
import { SearchResultPods } from './SearchResultPods';

export const SEARCH_RESULT_PODS_COMPONENT = 'spinnaker.core.search.infrastructure.searchResultPods.component';
module(SEARCH_RESULT_PODS_COMPONENT, []).component('searchResultPods', react2angular(SearchResultPods, ['results']));
