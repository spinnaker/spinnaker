import { module } from 'angular';

import { RecentlyViewedItems } from './RecentlyViewedItems';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const RECENTLY_VIEWED_ITEMS_COMPONENT = 'spinnaker.core.search.infrastructure.recentlyViewedItems.component';
module(RECENTLY_VIEWED_ITEMS_COMPONENT, []).component(
  'recentlyViewedItems',
  angularComponentFromReact(RecentlyViewedItems, 'recentlyViewedItems'),
);
