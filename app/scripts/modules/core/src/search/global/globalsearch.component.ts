import { module } from 'angular';
import { react2angular } from 'react2angular';

import { GlobalSearch } from './GlobalSearch';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const GLOBAL_SEARCH_COMPONENT = 'spinnaker.core.search.global.component';
module(GLOBAL_SEARCH_COMPONENT, []).component(
  'globalSearch',
  react2angular(withErrorBoundary(GlobalSearch, 'globalSearch')),
);
