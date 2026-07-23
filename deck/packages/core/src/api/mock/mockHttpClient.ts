import type { IHttpClientImplementation } from '../ApiService';
import type { IExpectBuilder } from './expectedRequest';
import { ExpectedRequest } from './expectedRequest';
import type { IDeferred, UrlArg, Verb } from './mockHttpUtils';
import { ReceivedRequest } from './receivedRequest';

interface IRequest {
  verb: Verb;
  url: string;
  params: object;
  data: any;
  expected: boolean;
  responseDeferred: IDeferred;
  flushResponse: () => void;
}

type RequestListener = (request: ReceivedRequest) => void;

/**
 * A mock HTTP client for unit tests.
 *
 * This class (and its counterparts) enables unit tests to expect that the code
 * being tested makes certain HTTP calls.  When the code makes the expected HTTP
 * calls, the responses are deferred until the unit test calls .flush().  Then,
 * any pending responses are flushed.
 */
export class MockHttpClient implements IHttpClientImplementation {
  public autoFlush = false;
  public failOnUnexpectedRequests = true;
  public expectedRequests: ExpectedRequest[] = [];
  public receivedRequests: ReceivedRequest[] = [];

  private isFlushing = () => this.requestListeners.length > 0;

  expect(verb: Verb, url?: UrlArg): IExpectBuilder {
    const expected = new ExpectedRequest(verb, url);
    this.expectedRequests.push(expected);
    return expected;
  }

  expectGET = (url?: UrlArg) => this.expect('GET', url);
  expectPUT = (url?: UrlArg) => this.expect('PUT', url);
  expectPOST = (url?: UrlArg) => this.expect('POST', url);
  expectDELETE = (url?: UrlArg) => this.expect('DELETE', url);
  expectPATCH = (url?: UrlArg) => this.expect('PATCH', url);

  request<T = any>(verb: Verb, iRequest: IRequest): PromiseLike<T> {
    const { url, params, data } = iRequest;
    const expectedRequest = this.expectedRequests.find((expect) => expect.isMatchAndUnfulfilled(verb, url, params));
    const request = new ReceivedRequest(verb, url, params, data, expectedRequest);
    this.receivedRequests.push(request);

    expectedRequest?.fulfill();

    if (this.isFlushing() || this.autoFlush) {
      request.flushResponse();
    }

    this.requestListeners.forEach((listener) => listener(request));

    return request.responseDeferred.promise;
  }

  get = <T = any>(request: IRequest): PromiseLike<T> => this.request('GET', request);
  put = <T = any>(request: IRequest): PromiseLike<T> => this.request('PUT', request);
  post = <T = any>(request: IRequest): PromiseLike<T> => this.request('POST', request);
  patch = <T = any>(request: IRequest): PromiseLike<T> => this.request('PATCH', request);
  delete = <T = any>(request: IRequest): PromiseLike<T> => this.request('DELETE', request);

  private requestListeners: RequestListener[] = [];
  private addRequestListener = (listener: RequestListener) => {
    this.requestListeners.push(listener);
    return () => (this.requestListeners = this.requestListeners.filter((x) => x !== listener));
  };

  private needsFlush() {
    const hasUnflushedRequests = this.receivedRequests.some((req) => !req.isFlushed());
    const hasUnfulfilledExpects = this.expectedRequests.some((expected) => !expected.isFulfilled());
    return hasUnflushedRequests || hasUnfulfilledExpects;
  }

  /**
   * Waits until all expected requests are received.
   * This function is async and should be await'ed in the unit test.
   *
   * 1) Flushes the response data for all received requests.
   *    The Promises in the code being tested will resolve or reject.
   * 2) Resolves when all expected requests have been fulfilled by a received request.
   *
   * If more requests are received during the wait period, they are also flushed.
   *
   * @param timeoutMs: How long to wait for all the expected requests to be received. (default: 100)
   */
  async flush({ timeoutMs = 100 } = {}): Promise<void> {
    if (!this.needsFlush()) {
      const message = 'There are no unflushed HTTP requests, nor are there any unfulfilled expected requests.';
      throw new Error(message);
    }

    let deregisterRequestListener: Function;
    let watchdog: ReturnType<typeof setTimeout>;

    const clearWatchdog = () => {
      if (watchdog !== undefined) {
        clearTimeout(watchdog);
        watchdog = undefined;
      }
    };

    try {
      await new Promise((resolve, reject) => {
        const resolvePromiseWhenFlushed = () => {
          const unflushedRequests = this.receivedRequests.filter((req) => !req.isFlushed());
          unflushedRequests.forEach((req) => req.flushResponse());
          const allExpectedRequestsFulfilled = this.expectedRequests.every((expected) => expected.isFulfilled());
          if (allExpectedRequestsFulfilled) {
            clearWatchdog();
            setImmediate(() => resolve(`All ${this.expectedRequests.length} expected requests are fulfilled`));
          }
        };

        // Re-run resolvePromiseWhenFlushed if a new request is received
        deregisterRequestListener = this.addRequestListener(resolvePromiseWhenFlushed);

        // If we haven't successfully flushed all requests and expects after timeoutMs, reject the promise returned from .flush()
        const timeoutMessage = `MockHttpClient.flush() timed out after ${timeoutMs}ms`;
        const message = [timeoutMessage].concat(this.getOutstandingExpectationMessages()).join('\n');
        watchdog = setTimeout(() => {
          watchdog = undefined;
          reject(message);
        }, timeoutMs);

        // Run the initial check
        resolvePromiseWhenFlushed();
      });
    } finally {
      clearWatchdog();
      if (deregisterRequestListener) {
        deregisterRequestListener();
      }
    }
  }

  private getOutstandingExpectationMessages() {
    const outstanding = this.expectedRequests.filter((expected) => !expected.isFulfilled());

    if (!outstanding.length) {
      return [];
    }

    return [
      `${outstanding.length} outstanding requests.`,
      'The following HTTP calls were expected, but were not received:',
      ...outstanding.map((expected) => `\t- HTTP ${expected.verb} ${expected.urlArg}`),
    ];
  }

  verifyNoOutstandingExpectation() {
    const outstanding = this.getOutstandingExpectationMessages();
    const message = outstanding.join('\n');
    expect(outstanding.length).toBe(0, message);
  }

  verifyNoOutstandingRequests() {
    const outstanding = this.receivedRequests.filter((req) => !req.isFlushed());

    if (outstanding.length) {
      const message = [
        `${outstanding.length} unflushed HTTP requests.  Call MockHttpClient.flush() to flush requests.`,
        'The following HTTP calls were initiated, but the responses were not flushed:',
        ...outstanding.map((request) => `\t- HTTP ${request.verb} ${request.url}`),
      ].join('\n');

      expect(outstanding.length).toBe(0, message);
    }
  }

  verifyNoUnexpectedRequests() {
    const unexpected = this.receivedRequests.filter((req) => !req.isExpected());

    if (this.failOnUnexpectedRequests && unexpected.length) {
      const message = [
        `${unexpected.length} unexpected HTTP requests.  Call MockHttpClient.failOnUnexpectedRequests = false to ignore these requests.`,
        'The following HTTP calls were received but were not expected:',
        ...unexpected.map((request) => `\t- HTTP ${request.verb} ${request.url}`),
      ].join('\n');

      expect(unexpected.length).toBe(0, message);
    }
  }
}
