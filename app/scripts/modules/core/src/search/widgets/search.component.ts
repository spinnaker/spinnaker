import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Search } from './Search';
import { SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';

export const SEARCH_COMPONENT = 'spinnaker.core.search.component';
module(SEARCH_COMPONENT, [])
  .component('tagSearch', react2angular(Search, ['query']))
  .run(() => {
    SearchFilterTypeRegistry.register({ key: 'stack', modifier: 'stack', text: 'Stack' });
  });
