import { SETTINGS } from '../../config/settings';
import { configureRouter } from '../../navigation/router';
import { SearchV1 } from './SearchV1';
import { SearchV2 } from './SearchV2';

import './infrastructure.states';

describe('infrastructure states', () => {
  const originalSearchVersion = SETTINGS.searchVersion;

  afterEach(() => {
    SETTINGS.searchVersion = originalSearchVersion;
  });

  it('registers direct V1 search and its one-shot route parameter', () => {
    SETTINGS.searchVersion = 1;
    const router = configureRouter();
    const searchState = router.stateRegistry.get('home.search');

    expect(searchState.views['main@'].component).toBe(SearchV1);
    expect(searchState.views['main@'].$type).toBe('react');
    expect(searchState.views['main@'].controller).toBeUndefined();
    expect(searchState.views['main@'].template).toBeUndefined();
    expect(searchState.views['main@'].templateUrl).toBeUndefined();
    expect(searchState.url).toContain('&route');
    expect(searchState.params.route.dynamic).toBe(true);
  });

  it('registers direct V2 search when configured', () => {
    SETTINGS.searchVersion = 2;
    const router = configureRouter();
    const searchState = router.stateRegistry.get('home.search');

    expect(searchState.views['main@'].component).toBe(SearchV2);
    expect(searchState.views['main@'].$type).toBe('react');
    expect(searchState.views['main@'].controller).toBeUndefined();
    expect(searchState.views['main@'].template).toBeUndefined();
    expect(searchState.views['main@'].templateUrl).toBeUndefined();
  });
});
