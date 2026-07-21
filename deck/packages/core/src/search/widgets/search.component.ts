import { module } from 'angular';

import { Search } from './Search';
import { SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const SEARCH_COMPONENT = 'spinnaker.core.search.component';
module(SEARCH_COMPONENT, [])
  .component('tagSearch', angularComponentFromReact(Search, 'tagSearch', ['params', 'onChange']))
  .run(() => {
    SearchFilterTypeRegistry.register({ key: 'account', name: 'Account' });
    SearchFilterTypeRegistry.register({ key: 'region', name: 'Region' });
    SearchFilterTypeRegistry.register({ key: 'stack', name: 'Stack' });
  });
