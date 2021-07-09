import { module } from 'angular';
import { react2angular } from 'react2angular';

import { FilterSearch } from './FilterSearch';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const FILTER_SEARCH_COMPONENT = 'spinnaker.application.filterSearch.component';

module(FILTER_SEARCH_COMPONENT, []).component(
  'filterSearch',
  react2angular(withErrorBoundary(FilterSearch, 'filterSearch'), ['helpKey', 'value', 'onSearchChange', 'onBlur']),
);
