import { module } from 'angular';
import { react2angular } from 'react2angular';

import { RecentlyViewedItems } from './RecentlyViewedItems';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const RECENTLY_VIEWED_ITEMS_COMPONENT = 'spinnaker.core.search.infrastructure.recentlyViewedItems.component';
module(RECENTLY_VIEWED_ITEMS_COMPONENT, []).component(
  'recentlyViewedItems',
  react2angular(withErrorBoundary(RecentlyViewedItems, 'recentlyViewedItems')),
);
