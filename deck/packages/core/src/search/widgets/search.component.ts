import { SearchFilterTypeRegistry } from './SearchFilterTypeRegistry';

export const SEARCH_COMPONENT = 'spinnaker.core.search.component';

export function registerSearchFilterTypes(): void {
  SearchFilterTypeRegistry.register({ key: 'account', name: 'Account' });
  SearchFilterTypeRegistry.register({ key: 'region', name: 'Region' });
  SearchFilterTypeRegistry.register({ key: 'stack', name: 'Stack' });
}
