import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Search } from './Search';
import { SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';

export const SEARCH_COMPONENT = 'spinnaker.core.search.component';
module(SEARCH_COMPONENT, [])
  .component('tagSearch', react2angular(Search, ['query', 'onChange']))
  .run(() => {
    SearchFilterTypeRegistry.register({ key: 'account', modifier: 'acct', text: 'Account' });
    SearchFilterTypeRegistry.register({ key: 'region', modifier: 'reg', text: 'Region' });
    SearchFilterTypeRegistry.register({ key: 'stack', modifier: 'stack', text: 'Stack' });
    SearchFilterTypeRegistry.register({ key: 'type', modifier: 'type', text: 'Type' });
  });
