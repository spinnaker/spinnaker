import { of } from 'rxjs';

import { UrlBuilder } from '../navigation';
import { SearchStatus, searchResultTypeRegistry } from '../search';
import './clusterSearchResultType';

describe('cluster search result type', () => {
  it('builds cluster links from result metadata', async () => {
    const buildFromMetadata = spyOn(UrlBuilder, 'buildFromMetadata').and.returnValue(
      '/clusters?acct=prod&q=cluster:payments',
    );
    const resultType = searchResultTypeRegistry.get('clusters');

    const result = await resultType
      .search(
        {} as any,
        of({
          results: [
            {
              account: 'prod',
              application: 'payments',
              cluster: 'payments',
              provider: 'aws',
              stack: '',
            },
          ],
          status: SearchStatus.FINISHED,
          type: { id: 'serverGroups' },
        } as any),
      )
      .toPromise();

    expect(buildFromMetadata).toHaveBeenCalledWith(
      jasmine.objectContaining({ account: 'prod', application: 'payments', cluster: 'payments', type: 'clusters' }),
    );
    expect(result.results[0].href).toBe('/clusters?acct=prod&q=cluster:payments');
  });
});
