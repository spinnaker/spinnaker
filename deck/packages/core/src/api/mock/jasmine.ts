import type { IHttpClientImplementation } from '../ApiService';
import { RequestBuilder, XhrHttpClient } from '../ApiService';
import { setAuthenticationHttpClient } from '../../authentication/AuthenticationInitializer';
import { MockHttpClient } from './mockHttpClient';

export interface IMockHttpClientConfig {
  autoFlush?: boolean;
  failOnUnexpectedRequests?: boolean;
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type RequestConfig = Parameters<IHttpClientImplementation['get']>[0];

function setHttpClient<T extends IHttpClientImplementation>(client: T): T {
  RequestBuilder.defaultHttpClient = client;
  setAuthenticationHttpClient(client);
  return client;
}

export class FailClosedHttpClient implements IHttpClientImplementation {
  public requests: Array<{ method: HttpMethod; url: string }> = [];

  public get = <T>(config: RequestConfig) => this.reject<T>('GET', config);
  public post = <T>(config: RequestConfig) => this.reject<T>('POST', config);
  public put = <T>(config: RequestConfig) => this.reject<T>('PUT', config);
  public patch = <T>(config: RequestConfig) => this.reject<T>('PATCH', config);
  public delete = <T>(config: RequestConfig) => this.reject<T>('DELETE', config);

  public verifyNoRequests(): void {
    const message = this.requests.map(({ method, url }) => `Unexpected HTTP ${method} ${url}`).join('\n');
    expect(this.requests.length).toBe(0, message);
  }

  private reject<T>(method: HttpMethod, config: RequestConfig): Promise<T> {
    this.requests.push({ method, url: config.url });
    return Promise.reject(
      new Error(`Unexpected HTTP ${method} ${config.url} in test; use mockHttpClient() or useRealHttpClient()`),
    );
  }
}

// Replaces the defaultHttpClient with a new MockHttpClient
// use this in a unit test's it() block to use the MockHttpClient.
export function mockHttpClient(config: IMockHttpClientConfig = {}) {
  const mockHttpClient = new MockHttpClient();
  Object.assign(mockHttpClient, config);
  return setHttpClient(mockHttpClient);
}

export function useRealHttpClient(): XhrHttpClient {
  return setHttpClient(new XhrHttpClient());
}

// Jasmine support:
// - keeps HTTP fail-closed while specs load and between test runs
// - asserts that no outstanding or unexpected requests were found in the MockHttpClient.
export function jasmineMockHttpSupport() {
  const failClosed = () => setHttpClient(new FailClosedHttpClient());
  const verifyFailClosed = () => {
    const http = RequestBuilder.defaultHttpClient;
    if (http instanceof FailClosedHttpClient) {
      http.verifyNoRequests();
    }
  };

  failClosed();

  beforeEach(() => {
    try {
      verifyFailClosed();
    } finally {
      failClosed();
    }
  });

  afterEach(() => {
    const http = RequestBuilder.defaultHttpClient;
    failClosed();
    if (http instanceof MockHttpClient) {
      http.verifyNoOutstandingExpectation();
      http.verifyNoOutstandingRequests();
      http.verifyNoUnexpectedRequests();
    } else if (http instanceof FailClosedHttpClient) {
      http.verifyNoRequests();
    }
  });

  afterAll(() => {
    verifyFailClosed();
  });
}
