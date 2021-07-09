import { isEqual, isMatch } from 'lodash';

import { SETTINGS } from '../../config';

import { deferred, isSuccessStatus, UrlArg, Verb } from './mockHttpUtils';
import { ReceivedRequest } from './receivedRequest';

function parseParams(queryString: string): Record<string, string | string[]> {
  const paramTuples = queryString.split('&').map((param) => param.split('=')) as Array<[string, string]>;

  return paramTuples.reduce((paramsObj, [key, value]) => {
    const currentValue = paramsObj[key];
    if (typeof currentValue === 'string') {
      paramsObj[key] = [currentValue, value];
    } else if (Array.isArray(currentValue)) {
      paramsObj[key] = paramsObj[key].concat(value);
    } else {
      paramsObj[key] = value;
    }
    return paramsObj;
  }, {} as Record<string, string | string[]>);
}

/**
 * This class represents an HTTP request that is expected to be made by the code under test.
 * Instances are created by MockHttpClient.expect -- this class should not be used directly.
 *
 * This class exposes a promise (this.fulfilledDeferred.promise).
 * The promise will be resolved when the code under test makes the expected HTTP request.
 *
 * ExpectedRequest also stores the (optional) mock response status/data to return to the code under test.
 */
export class ExpectedRequest implements IExpectBuilder {
  verb: Verb;
  urlArg: UrlArg;
  path: string;
  expectedParams: {} = {};
  exactParams = false;
  onResponseReceivedCallback: (request: ReceivedRequest) => void;

  /**
   * Creates a new ExpectRequest object
   * @param verb GET/PUT/POST/DELETE
   * @param urlArg the URL to match: string, regexp, or matcher
   *            If a string, query params are parsed out of the string and used as a partial match against each request
   *            e.g.: '/foo/bar?param1=val1&param2=val2'
   */
  constructor(verb: Verb, urlArg: UrlArg = /.*/) {
    this.verb = verb;
    this.urlArg = urlArg;

    if (typeof urlArg === 'string') {
      const [path, query] = urlArg.split('?');
      this.path = path;
      // If there is a query string in the url, parse into params
      this.expectedParams = query ? parseParams(query) : {};
    }
  }

  public response = {
    status: 200,
    data: null as any,
  };

  public isFulfilled = () => this.fulfilledDeferred.settled;
  public fulfilledDeferred = deferred();

  /**
   * Marks the expected response as fulfilled and settles the promise using the mock response.
   */
  public fulfill() {
    const { status, data } = this.response;
    if (isSuccessStatus(status)) {
      this.fulfilledDeferred.resolve(data);
    } else {
      this.fulfilledDeferred.promise.catch(() => 0);
      this.fulfilledDeferred.reject({ status, data });
    }
  }

  isMatchAndUnfulfilled(verb: Verb, url: string, params: object) {
    return !this.isFulfilled() && this.matchVerb(verb) && this.matchUrl(url) && this.matchParams(params);
  }

  matchVerb = (verb: Verb) => this.verb === verb;

  matchUrl = (requestUrl: string) => {
    const { urlArg, path } = this;

    if (typeof path === 'string') {
      return requestUrl === path || requestUrl === SETTINGS.gateUrl + path;
    } else if (urlArg instanceof RegExp) {
      return !!urlArg.exec(requestUrl);
    } else if (typeof urlArg === 'function') {
      return urlArg(requestUrl);
    }
    return false;
  };

  matchParams = (requestParams: object) => {
    const { exactParams, expectedParams } = this;
    return exactParams ? isEqual(requestParams, expectedParams) : isMatch(requestParams, expectedParams);
  };

  onRequestReceived(callback: (request: ReceivedRequest) => void) {
    this.onResponseReceivedCallback = callback;
    return this;
  }

  withParams(expectedParams: {}, exact = false): IExpectBuilder {
    this.expectedParams = expectedParams;
    this.exactParams = exact;
    return this;
  }

  respond(status: number, data?: any): IExpectBuilder {
    this.response.status = status;
    this.response.data = data;
    return this;
  }
}

export interface IExpectBuilder {
  /**
   * This callback is invoked when the expected request is received
   * @param callback The callback to invoke.
   *        The callback receives the ReceivedRequest object
   */
  onRequestReceived(callback: (request: ReceivedRequest) => void): IExpectBuilder;
  /**
   * Match only requests with the expected query parameters
   * @param expectedParams the required params to match
   * @param exact match exact params
   *        false: (default) actual params must include expected params
   *        true: actual params exactly match expected params
   */
  withParams(expectedParams: {}, exact?: boolean): IExpectBuilder;

  /**
   * Set the response for the expected request
   * @param status the HTTP status code
   *        - For 2xx status codes, resolves the promise.
   *        - For other status codes, rejects the promise
   * @param data the response payload
   */
  respond(status: number, data?: any): IExpectBuilder;
}
