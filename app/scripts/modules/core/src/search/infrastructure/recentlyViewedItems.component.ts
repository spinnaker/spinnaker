import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { RecentlyViewedItems } from './RecentlyViewedItems';

export const RECENTLY_VIEWED_ITEMS_COMPONENT = 'spinnaker.core.search.infrastructure.recentlyViewedItems.component';
module(RECENTLY_VIEWED_ITEMS_COMPONENT, []).component(
  'recentlyViewedItems',
  react2angular(withErrorBoundary(RecentlyViewedItems, 'recentlyViewedItems')),
);
