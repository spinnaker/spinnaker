import { SETTINGS } from '../../config/settings';
import { getSearchQuery } from './utils';

describe('global search query params', () => {
  const originalSearchVersion = SETTINGS.searchVersion;

  afterEach(() => {
    SETTINGS.searchVersion = originalSearchVersion;
  });

  it('uses q and omits V2 tabs for V1 search', () => {
    SETTINGS.searchVersion = 1;

    expect(getSearchQuery('compute', 'applications')).toEqual({ q: 'compute' });
  });

  it('uses key and tab for V2 search', () => {
    SETTINGS.searchVersion = 2;

    expect(getSearchQuery('compute', 'applications')).toEqual({ key: 'compute', tab: 'applications' });
  });
});
