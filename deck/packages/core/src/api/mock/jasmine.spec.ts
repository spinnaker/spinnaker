import { RequestBuilder } from '../ApiService';
import { FailClosedHttpClient } from './jasmine';

const httpClientWhileLoadingSpecs = RequestBuilder.defaultHttpClient;

describe('Karma HTTP isolation', () => {
  it('is fail-closed while test modules are loading', () => {
    expect(httpClientWhileLoadingSpecs).toEqual(jasmine.any(FailClosedHttpClient));
  });

  it('rejects unmocked requests locally without creating a network request', async () => {
    const createRequest = spyOn(window, 'XMLHttpRequest').and.callThrough();
    const client = new FailClosedHttpClient();
    const request = new RequestBuilder(undefined, client, 'http://localhost:8084').path('unmocked').get();

    await expectAsync(request).toBeRejectedWithError(
      'Unexpected HTTP GET http://localhost:8084/unmocked in test; use mockHttpClient() or useRealHttpClient()',
    );
    expect(createRequest).not.toHaveBeenCalled();
    expect(client.requests).toEqual([{ method: 'GET', url: 'http://localhost:8084/unmocked' }]);
  });
});
