import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Search } from './Search';
import { SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const SEARCH_COMPONENT = 'spinnaker.core.search.component';
module(SEARCH_COMPONENT, [])
  .component('tagSearch', react2angular(withErrorBoundary(Search, 'tagSearch'), ['params', 'onChange']))
  .run(() => {
    SearchFilterTypeRegistry.register({ key: 'account', name: 'Account' });
    SearchFilterTypeRegistry.register({ key: 'region', name: 'Region' });
    SearchFilterTypeRegistry.register({ key: 'stack', name: 'Stack' });
  });
