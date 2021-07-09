import { IHttpClientImplementation, RequestBuilder } from '../ApiService';
import { MockHttpClient } from './mockHttpClient';

export interface IMockHttpClientConfig {
  autoFlush?: boolean;
  failOnUnexpectedRequests?: boolean;
}

// Replaces the defaultHttpClient with a new MockHttpClient
// use this in a unit test's it() block to use the MockHttpClient.
export function mockHttpClient(config: IMockHttpClientConfig = {}) {
  const mockHttpClient = new MockHttpClient();
  Object.assign(mockHttpClient, config);
  RequestBuilder.defaultHttpClient = mockHttpClient;
  return mockHttpClient;
}

// Jasmine support:
// - restores the original defaultHttpClient after each test run
// - asserts that no outstanding or unexpected requests were found in the MockHttpClient.
export function jasmineMockHttpSupport() {
  let original: IHttpClientImplementation;

  beforeEach(() => {
    return (original = RequestBuilder.defaultHttpClient);
  });

  afterEach(() => {
    const http = RequestBuilder.defaultHttpClient;
    RequestBuilder.defaultHttpClient = original;
    if (http instanceof MockHttpClient) {
      http.verifyNoOutstandingExpectation();
      http.verifyNoOutstandingRequests();
      http.verifyNoUnexpectedRequests();
    }
  });
}
