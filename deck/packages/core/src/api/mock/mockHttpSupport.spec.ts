import { RequestBuilder } from '../ApiService';
import { FailClosedHttpClient } from './mockHttpSupport';

const httpClientWhileLoadingSpecs = RequestBuilder.defaultHttpClient;

describe('Mock HTTP isolation', () => {
  it('is fail-closed while test modules are loading', () => {
    expect(httpClientWhileLoadingSpecs).toEqual(expect.any(FailClosedHttpClient));
  });

  it('rejects unmocked requests locally without creating a network request', async () => {
    const createRequest = vi.spyOn(window, 'XMLHttpRequest');
    const client = new FailClosedHttpClient();
    const request = new RequestBuilder(undefined, client, 'http://localhost:8084').path('unmocked').get();

    await expectAsync(request).toBeRejectedWithError(
      'Unexpected HTTP GET http://localhost:8084/unmocked in test; use mockHttpClient() or useRealHttpClient()',
    );
    expect(createRequest).not.toHaveBeenCalled();
    expect(client.requests).toEqual([{ method: 'GET', url: 'http://localhost:8084/unmocked' }]);
  });
});
