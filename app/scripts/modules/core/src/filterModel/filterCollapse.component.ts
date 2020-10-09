import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { module } from 'angular';
import { react2angular } from 'react2angular';
import { FilterCollapse } from './FilterCollapse';

export const FILTER_COLLAPSE_COMPONENT = 'spinnaker.core.filterModel.filterCollapse.component';
module(FILTER_COLLAPSE_COMPONENT, []).component(
  'filterCollapse',
  react2angular(withErrorBoundary(FilterCollapse, 'filterCollapse')),
);
