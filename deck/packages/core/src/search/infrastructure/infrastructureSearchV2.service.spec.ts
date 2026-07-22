import { toArray } from 'rxjs/operators';

import { searchResultTypeRegistry } from '../searchResult/searchResultType.registry';
import type { SearchResultType } from '../searchResult/searchResultType';
import { InfrastructureSearchServiceV2 } from './infrastructureSearchV2.service';

describe('InfrastructureSearchServiceV2', () => {
  it('reads empty-search result types when searching, not when the module loads', async () => {
    const resultType = {
      id: 'late-registered-type',
      order: Number.MAX_SAFE_INTEGER,
      displayName: 'Late Registered Type',
      displayFormatter: () => 'Late Registered Type',
      search: () => undefined,
    } as SearchResultType;

    searchResultTypeRegistry.register(resultType);

    const results = await InfrastructureSearchServiceV2.search({}).pipe(toArray()).toPromise();

    expect(results.some((result) => result.type.id === resultType.id)).toBe(true);
  });
});
